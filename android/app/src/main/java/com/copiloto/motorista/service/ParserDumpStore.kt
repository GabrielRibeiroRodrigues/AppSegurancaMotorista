package com.copiloto.motorista.service

import android.content.Context
import java.io.File

/**
 * Stores structured dumps of the accessibility node tree for debugging the parser
 * (Module A). Dumps are written to the app's internal storage and the newest
 * [MAX_DUMPS] are kept so the folder cannot grow without bound.
 */
object ParserDumpStore {

    const val MAX_DUMPS = 30
    private const val DIR_NAME = "parser_dumps"

    data class DumpRef(val name: String, val lastModified: Long)

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Writes a dump file, then trims the folder to [MAX_DUMPS]. Returns the file. */
    fun save(context: Context, label: String, content: String, timestampMillis: Long = System.currentTimeMillis()): File {
        val dir = dir(context)
        val file = File(dir, "dump_${timestampMillis}_$label.txt")
        file.writeText(content)
        trim(dir)
        return file
    }

    fun count(context: Context): Int =
        dir(context).listFiles { f -> f.isFile && f.extension == "txt" }?.size ?: 0

    fun clear(context: Context): Int {
        val files = dir(context).listFiles { f -> f.isFile && f.extension == "txt" } ?: return 0
        return files.count { it.delete() }
    }

    private fun trim(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.extension == "txt" } ?: return
        val refs = files.map { DumpRef(it.name, it.lastModified()) }
        val toRemove = selectForTrim(refs, MAX_DUMPS).map { it.name }.toSet()
        if (toRemove.isEmpty()) return
        files.forEach { if (it.name in toRemove) it.delete() }
    }

    /**
     * Pure retention logic: returns the oldest dumps beyond [keep] (to be deleted).
     */
    fun selectForTrim(dumps: List<DumpRef>, keep: Int): List<DumpRef> {
        if (dumps.size <= keep) return emptyList()
        return dumps.sortedByDescending { it.lastModified }.drop(keep)
    }
}
