package com.copiloto.motorista.service

import com.copiloto.motorista.service.ParserDumpStore.DumpRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserDumpStoreTest {

    private fun dump(name: String, modified: Long) = DumpRef(name, modified)

    @Test
    fun `keeps all when under the retention limit`() {
        val dumps = listOf(dump("a", 1), dump("b", 2))
        assertTrue(ParserDumpStore.selectForTrim(dumps, keep = 30).isEmpty())
    }

    @Test
    fun `keeps all when exactly at the limit`() {
        val dumps = (1..5).map { dump("d$it", it.toLong()) }
        assertTrue(ParserDumpStore.selectForTrim(dumps, keep = 5).isEmpty())
    }

    @Test
    fun `removes the oldest beyond the limit`() {
        val dumps = listOf(
            dump("newest", 100),
            dump("old", 10),
            dump("mid", 50),
        )
        val removed = ParserDumpStore.selectForTrim(dumps, keep = 2)
        assertEquals(listOf("old"), removed.map { it.name })
    }

    @Test
    fun `removes multiple oldest when far over the limit`() {
        val dumps = (1..6).map { dump("d$it", it.toLong()) }
        val removed = ParserDumpStore.selectForTrim(dumps, keep = 2).map { it.name }.sorted()
        // Keep the two newest (d5, d6); remove d1..d4.
        assertEquals(listOf("d1", "d2", "d3", "d4"), removed)
    }
}
