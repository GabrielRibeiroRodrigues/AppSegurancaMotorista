package com.copiloto.motorista.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.copiloto.motorista.CopilotoApp
import com.copiloto.motorista.data.model.RideOffer
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.engine.RideParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scraping Engine (Module A). Watches the configured rideshare apps, extracts the
 * text from the current window's node tree when an offer screen appears, parses it
 * into a [RideOffer], and forwards complete offers to the [OverlayService].
 *
 * To help tune the parser against real (and changing) app layouts, it also writes
 * a structured dump of the node tree via [ParserDumpStore] — automatically when a
 * transport app is on screen but parsing fails, or on demand via [requestDump].
 */
class RideAccessibilityService : AccessibilityService() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastSignature: String? = null
    private var lastEmittedAt = 0L
    private var lastDumpAt = 0L

    /**
     * Packages the driver wants read, mapped to the label to display. Kept in
     * sync with [com.copiloto.motorista.data.settings.MonitoredAppsStore] so the
     * allow-list is user-configurable (built-ins + apps the driver added). The
     * XML config no longer restricts packages; this map is the gate.
     */
    @Volatile
    private var monitored: Map<String, String> = RideSource.BUILT_INS.associate {
        it.packageName!! to it.displayName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ioScope.launch {
            runCatching {
                (application as CopilotoApp).container.monitoredAppsStore.monitoredLabels
                    .collectLatest { monitored = it }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val label = monitored[packageName] ?: return
        val builtIn = RideSource.fromPackage(packageName)
        // Built-ins keep their enum source; a driver-added app uses OTHER + its label.
        val source = if (builtIn != RideSource.UNKNOWN) builtIn else RideSource.OTHER
        val customLabel = if (builtIn == RideSource.UNKNOWN) label else null

        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectText(root, texts)

        // Manual, developer-triggered dump: capture whatever is on screen now.
        if (consumeDumpRequest()) {
            dumpTree(root, source, label = "manual")
        }

        if (texts.isEmpty()) return

        val parsed = RideParser.parse(texts, source)
        val offer = parsed?.copy(sourceLabel = customLabel)
        if (offer == null || !offer.isComplete) {
            // Parser failed on a transport screen — save a dump for later analysis.
            maybeDumpOnFailure(root, source)
            return
        }

        if (shouldEmit(offer)) {
            OverlayService.showOffer(this, offer)
        }
    }

    override fun onInterrupt() {
        // No long-running work to interrupt.
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }

    /** Depth-first collection of visible text, bounded to keep traversal cheap. */
    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size >= MAX_NODES) return
        node.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
        node.contentDescription?.let { if (it.isNotBlank()) out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out)
        }
    }

    /**
     * Rideshare apps fire many content-changed events for the same offer; only
     * emit when the offer's key figures change or enough time has passed.
     */
    private fun shouldEmit(offer: RideOffer): Boolean {
        val signature = "${offer.source}|${offer.grossPrice}|${offer.distanceKm}|${offer.timeMinutes}"
        val now = System.currentTimeMillis()
        val isDuplicate = signature == lastSignature && (now - lastEmittedAt) < DEDUPE_WINDOW_MS
        if (isDuplicate) return false
        lastSignature = signature
        lastEmittedAt = now
        return true
    }

    /** Throttled automatic dump when parsing fails on a transport screen. */
    private fun maybeDumpOnFailure(root: AccessibilityNodeInfo, source: RideSource) {
        val now = System.currentTimeMillis()
        if (now - lastDumpAt < DUMP_THROTTLE_MS) return
        lastDumpAt = now
        dumpTree(root, source, label = "fail")
    }

    /** Builds a structured text dump of the tree (on this thread) and writes it off-thread. */
    private fun dumpTree(root: AccessibilityNodeInfo, source: RideSource, label: String) {
        val timestamp = DATE_FORMAT.format(Date())
        val builder = StringBuilder()
        builder.append("# Copiloto parser dump\n")
        builder.append("source=${source.name} package=${source.packageName}\n")
        builder.append("time=$timestamp\n")
        builder.append("---\n")
        appendNode(root, 0, builder)
        val content = builder.toString()
        ioScope.launch {
            runCatching { ParserDumpStore.save(this@RideAccessibilityService, label, content) }
        }
    }

    private fun appendNode(node: AccessibilityNodeInfo?, depth: Int, out: StringBuilder) {
        if (node == null || depth > MAX_DEPTH) return
        val indent = "  ".repeat(depth)
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val className = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val id = node.viewIdResourceName?.substringAfterLast('/') ?: ""
        val text = node.text?.toString()?.replace('\n', ' ') ?: ""
        val desc = node.contentDescription?.toString()?.replace('\n', ' ') ?: ""

        out.append(indent)
            .append(className)
            .append(if (id.isNotEmpty()) " #$id" else "")
            .append(if (text.isNotEmpty()) "  text=\"$text\"" else "")
            .append(if (desc.isNotEmpty()) "  desc=\"$desc\"" else "")
            .append("  $bounds")
            .append('\n')

        for (i in 0 until node.childCount) {
            appendNode(node.getChild(i), depth + 1, out)
        }
    }

    companion object {
        private const val MAX_NODES = 300
        private const val MAX_DEPTH = 40
        private const val DEDUPE_WINDOW_MS = 15_000L
        private const val DUMP_THROTTLE_MS = 8_000L
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        @Volatile
        private var dumpRequested = false

        /** Asks the running service to dump the next transport screen it sees. */
        fun requestDump() {
            dumpRequested = true
        }

        private fun consumeDumpRequest(): Boolean {
            if (!dumpRequested) return false
            dumpRequested = false
            return true
        }
    }
}
