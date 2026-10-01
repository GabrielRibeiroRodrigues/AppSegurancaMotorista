package com.copiloto.motorista.service

import android.content.Context
import java.io.File

/**
 * Scoped storage and the 5 GB rotating buffer for the safety dashcam (Module E).
 *
 * Recordings live in the app's scoped external files dir, so they are private to
 * the app and removed on uninstall. When the folder grows past the budget, the
 * oldest segments are deleted first.
 */
object DashcamStorage {

    const val DEFAULT_MAX_BYTES: Long = 5L * 1024 * 1024 * 1024 // 5 GB
    const val SEGMENT_EXTENSION = "mp4"

    /** A finalized recording segment, as the rotation logic sees it. */
    data class Segment(val name: String, val sizeBytes: Long, val lastModified: Long)

    /** Returns the scoped directory where trip recordings are stored. */
    fun recordingsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    /** Builds a timestamped segment file in the recordings directory. */
    fun newSegmentFile(context: Context, timestampMillis: Long): File =
        File(recordingsDir(context), "trip_$timestampMillis.$SEGMENT_EXTENSION")

    /**
     * Pure selection: given the current segments, returns those that must be
     * deleted (oldest first) so the total stays within [maxBytes]. Returns an
     * empty list when the buffer already fits.
     */
    fun selectForDeletion(segments: List<Segment>, maxBytes: Long): List<Segment> {
        var total = segments.sumOf { it.sizeBytes }
        if (total <= maxBytes) return emptyList()

        val toDelete = mutableListOf<Segment>()
        for (segment in segments.sortedBy { it.lastModified }) {
            if (total <= maxBytes) break
            toDelete.add(segment)
            total -= segment.sizeBytes
        }
        return toDelete
    }

    /**
     * Enforces the budget against the real directory, deleting the oldest
     * recordings as needed. Returns the number of files deleted.
     */
    fun enforceBudget(dir: File, maxBytes: Long = DEFAULT_MAX_BYTES): Int {
        val files = dir.listFiles { file -> file.isFile && file.extension == SEGMENT_EXTENSION }
            ?: return 0
        val segments = files.map { Segment(it.name, it.length(), it.lastModified()) }
        val toDelete = selectForDeletion(segments, maxBytes).map { it.name }.toSet()
        if (toDelete.isEmpty()) return 0

        var deleted = 0
        for (file in files) {
            if (file.name in toDelete && file.delete()) deleted++
        }
        return deleted
    }
}
