package com.copiloto.motorista.service

import com.copiloto.motorista.service.DashcamStorage.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashcamStorageTest {

    private fun seg(name: String, size: Long, modified: Long) = Segment(name, size, modified)

    @Test
    fun `keeps all segments when under budget`() {
        val segments = listOf(
            seg("a", 100, 1),
            seg("b", 100, 2),
        )
        assertTrue(DashcamStorage.selectForDeletion(segments, maxBytes = 1000).isEmpty())
    }

    @Test
    fun `keeps all segments when exactly at budget`() {
        val segments = listOf(
            seg("a", 400, 1),
            seg("b", 600, 2),
        )
        assertTrue(DashcamStorage.selectForDeletion(segments, maxBytes = 1000).isEmpty())
    }

    @Test
    fun `deletes oldest first until within budget`() {
        val segments = listOf(
            seg("newest", 500, 30),
            seg("oldest", 500, 10),
            seg("middle", 500, 20),
        )
        val deleted = DashcamStorage.selectForDeletion(segments, maxBytes = 1000)
        // Total 1500 > 1000; removing the single oldest (500) brings it to 1000.
        assertEquals(listOf("oldest"), deleted.map { it.name })
    }

    @Test
    fun `deletes multiple oldest when needed`() {
        val segments = listOf(
            seg("s1", 500, 10),
            seg("s2", 500, 20),
            seg("s3", 500, 30),
            seg("s4", 500, 40),
        )
        val deleted = DashcamStorage.selectForDeletion(segments, maxBytes = 1000)
        // Total 2000 -> must drop to <= 1000, i.e. remove the two oldest.
        assertEquals(listOf("s1", "s2"), deleted.map { it.name })
    }

    @Test
    fun `empty list yields nothing to delete`() {
        assertTrue(DashcamStorage.selectForDeletion(emptyList(), maxBytes = 1000).isEmpty())
    }

    @Test
    fun `five gigabyte budget constant is correct`() {
        assertEquals(5L * 1024 * 1024 * 1024, DashcamStorage.DEFAULT_MAX_BYTES)
    }
}
