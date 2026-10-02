package com.copiloto.motorista.service.webrtc

import android.content.Context
import com.copiloto.motorista.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap

/**
 * Broadcasts the driver's camera + microphone to the Central de Operações over
 * WebRTC (DesafioMaker — live video). Acts as the "driver" peer: connects to the
 * signaling server for a given [alertId] and opens one PeerConnection per viewer.
 *
 * The signaling protocol mirrors the web dashboard exactly, so the operator's
 * browser can view the stream peer-to-peer.
 */
class WebRtcBroadcaster(
    private val appContext: Context,
    private val signalingUrl: String,
    private val alertId: String,
) {

    private val eglBase: EglBase = EglBase.create()
    private val http = OkHttpClient()
    private var factory: PeerConnectionFactory? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var ws: WebSocket? = null
    private val peers = ConcurrentHashMap<String, PeerConnection>()

    @Volatile
    private var closed = false

    fun start() {
        initFactory()
        if (!startCapture()) {
            stop()
            return
        }
        connectSignaling()
    }

    private fun initFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions(),
        )
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
    }

    private fun startCapture(): Boolean {
        val pcFactory = factory ?: return false
        val cam = createCameraCapturer() ?: return false
        capturer = cam
        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        surfaceHelper = helper
        val vSource = pcFactory.createVideoSource(false)
        videoSource = vSource
        cam.initialize(helper, appContext, vSource.capturerObserver)
        runCatching { cam.startCapture(1280, 720, 30) }
        videoTrack = pcFactory.createVideoTrack("video0", vSource).apply { setEnabled(true) }

        val aSource = pcFactory.createAudioSource(MediaConstraints())
        audioSource = aSource
        audioTrack = pcFactory.createAudioTrack("audio0", aSource).apply { setEnabled(true) }
        return true
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val names = enumerator.deviceNames
        // Prefer the back camera (shows the scene); fall back to front, then any.
        names.firstOrNull { enumerator.isBackFacing(it) }?.let {
            enumerator.createCapturer(it, null)?.let { c -> return c }
        }
        names.firstOrNull { enumerator.isFrontFacing(it) }?.let {
            enumerator.createCapturer(it, null)?.let { c -> return c }
        }
        names.firstOrNull()?.let { return enumerator.createCapturer(it, null) }
        return null
    }

    private fun connectSignaling() {
        val request = Request.Builder().url(signalingUrl).build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                send(JSONObject().put("type", "register-driver").put("alertId", alertId))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSignal(text)
            }
        })
    }

    private fun handleSignal(text: String) {
        if (closed) return
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (msg.optString("alertId") != alertId) return
        when (msg.optString("type")) {
            "watch-request" -> createPeerForViewer(msg.optString("viewerId"))
            "answer" -> {
                val viewerId = msg.optString("viewerId")
                val sdp = msg.optJSONObject("sdp") ?: return
                peers[viewerId]?.setRemoteDescription(
                    LoggingSdpObserver(),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp.optString("sdp")),
                )
            }
            "ice-candidate" -> {
                val viewerId = msg.optString("viewerId")
                val cand = msg.optJSONObject("candidate") ?: return
                peers[viewerId]?.addIceCandidate(
                    IceCandidate(
                        cand.optString("sdpMid"),
                        cand.optInt("sdpMLineIndex"),
                        cand.optString("candidate"),
                    ),
                )
            }
            "viewer-left" -> {
                val viewerId = msg.optString("viewerId")
                peers.remove(viewerId)?.close()
            }
        }
    }

    private fun createPeerForViewer(viewerId: String) {
        if (viewerId.isEmpty() || closed) return
        val pcFactory = factory ?: return
        val config = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = pcFactory.createPeerConnection(
            config,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    send(
                        JSONObject()
                            .put("type", "ice-candidate")
                            .put("alertId", alertId)
                            .put("targetId", viewerId)
                            .put(
                                "candidate",
                                JSONObject()
                                    .put("candidate", candidate.sdp)
                                    .put("sdpMid", candidate.sdpMid)
                                    .put("sdpMLineIndex", candidate.sdpMLineIndex),
                            ),
                    )
                }

                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: org.webrtc.MediaStream?) {}
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
                override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
            },
        ) ?: return

        val streamIds = listOf("stream0")
        videoTrack?.let { pc.addTrack(it, streamIds) }
        audioTrack?.let { pc.addTrack(it, streamIds) }
        peers[viewerId] = pc

        pc.createOffer(
            object : LoggingSdpObserver() {
                override fun onCreateSuccess(description: SessionDescription) {
                    pc.setLocalDescription(LoggingSdpObserver(), description)
                    send(
                        JSONObject()
                            .put("type", "offer")
                            .put("alertId", alertId)
                            .put("targetId", viewerId)
                            .put(
                                "sdp",
                                JSONObject()
                                    .put("type", "offer")
                                    .put("sdp", description.description),
                            ),
                    )
                }
            },
            MediaConstraints(),
        )
    }

    private fun send(message: JSONObject) {
        runCatching { ws?.send(message.toString()) }
    }

    fun stop() {
        if (closed) return
        closed = true
        peers.values.forEach { runCatching { it.close() } }
        peers.clear()
        runCatching { ws?.close(1000, null) }
        ws = null
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { eglBase.release() }
    }

    private companion object {
        // STUN discovers the public IP; TURN relays the media when direct P2P fails
        // (common on mobile/symmetric NAT). TURN is added only when configured via
        // the TURN_* BuildConfig fields (see build.gradle.kts / Gradle properties).
        val ICE_SERVERS: List<PeerConnection.IceServer> = buildList {
            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
            if (BuildConfig.TURN_URL.isNotEmpty()) {
                add(
                    PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                        .setUsername(BuildConfig.TURN_USERNAME)
                        .setPassword(BuildConfig.TURN_CREDENTIAL)
                        .createIceServer(),
                )
            }
        }
    }
}

/** No-op SDP observer; subclasses override only what they need. */
open class LoggingSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
