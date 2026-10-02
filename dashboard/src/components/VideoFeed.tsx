import { useEffect, useRef, useState } from 'react';
import { VideoOffIcon } from './icons';

interface VideoFeedProps {
  alertId: string;
}

const API_URL = import.meta.env.VITE_SERVER_URL ?? 'http://localhost:4000';
const SIGNALING_URL = `${API_URL.replace(/^http/, 'ws')}/signaling`;
const CONNECT_TIMEOUT_MS = 10000;

const ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
];

type FeedStatus = 'connecting' | 'live' | 'unavailable' | 'ended';

/** Vídeo ao vivo da câmera do motorista, via WebRTC — conecta como
 * "viewer" na sinalização do backend e recebe a transmissão direto do
 * navegador do motorista (peer-to-peer, o vídeo não passa pelo servidor). */
export function VideoFeed({ alertId }: VideoFeedProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [status, setStatus] = useState<FeedStatus>('connecting');

  useEffect(() => {
    let cancelled = false;
    let pc: RTCPeerConnection | null = null;
    const socket = new WebSocket(SIGNALING_URL);

    const timeout = window.setTimeout(() => {
      if (!cancelled && status !== 'live') setStatus('unavailable');
    }, CONNECT_TIMEOUT_MS);

    socket.onopen = () => socket.send(JSON.stringify({ type: 'watch', alertId }));

    socket.onmessage = async (event) => {
      if (cancelled) return;
      const msg = JSON.parse(event.data);
      if (msg.alertId !== alertId) return;

      if (msg.type === 'offer') {
        pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
        pc.ontrack = (trackEvent) => {
          if (videoRef.current) videoRef.current.srcObject = trackEvent.streams[0];
          setStatus('live');
        };
        pc.onicecandidate = (iceEvent) => {
          if (iceEvent.candidate) {
            socket.send(JSON.stringify({ type: 'ice-candidate', alertId, candidate: iceEvent.candidate }));
          }
        };
        await pc.setRemoteDescription(new RTCSessionDescription(msg.sdp));
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        socket.send(JSON.stringify({ type: 'answer', alertId, sdp: pc.localDescription }));
      } else if (msg.type === 'ice-candidate' && msg.candidate) {
        await pc?.addIceCandidate(new RTCIceCandidate(msg.candidate));
      } else if (msg.type === 'driver-left') {
        setStatus('ended');
      }
    };

    return () => {
      cancelled = true;
      window.clearTimeout(timeout);
      pc?.close();
      socket.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alertId]);

  return (
    <div className="relative flex h-48 items-center justify-center overflow-hidden rounded-xl bg-black ring-1 ring-white/5">
      <video ref={videoRef} autoPlay playsInline muted className="h-full w-full object-cover" />
      {status !== 'live' && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-slate-950/90 text-slate-500">
          <VideoOffIcon className="h-6 w-6" />
          <p className="text-xs">
            {status === 'connecting' && 'Conectando à câmera do motorista...'}
            {status === 'unavailable' && 'Câmera não disponível para este alerta.'}
            {status === 'ended' && 'Transmissão encerrada.'}
          </p>
        </div>
      )}
      {status === 'live' && (
        <span className="absolute left-2 top-2 flex items-center gap-1 rounded-full bg-red-600/90 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-white">
          <span className="h-1.5 w-1.5 rounded-full bg-white pulse-live" />
          Ao vivo
        </span>
      )}
    </div>
  );
}
