package com.copiloto.motorista.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.copiloto.motorista.data.model.RideOffer
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.engine.RideParser

/**
 * Scraping Engine (Module A). Watches the configured rideshare apps, extracts the
 * text from the current window's node tree when an offer screen appears, parses it
 * into a [RideOffer], and forwards complete offers to the [OverlayService].
 *
 * The set of events and packages is also declared in
 * `res/xml/accessibility_service_config.xml`; the checks here are a defensive
 * second filter.
 */
class RideAccessibilityService : AccessibilityService() {

    private var lastSignature: String? = null
    private var lastEmittedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val source = RideSource.fromPackage(packageName)
        if (source == RideSource.UNKNOWN) return

        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectText(root, texts)
        if (texts.isEmpty()) return

        val offer = RideParser.parse(texts, source) ?: return
        if (!offer.isComplete) return

        if (shouldEmit(offer)) {
            OverlayService.showOffer(this, offer)
        }
    }

    override fun onInterrupt() {
        // No long-running work to interrupt.
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

    private companion object {
        const val MAX_NODES = 300
        const val DEDUPE_WINDOW_MS = 15_000L
    }
}
