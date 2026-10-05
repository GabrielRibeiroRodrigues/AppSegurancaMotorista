package com.copiloto.motorista.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the ESP32 panic-button payload parsing and replay de-duplication. */
class PanicButtonBleTest {

    private fun payload(event: Int, seq: Long): ByteArray = byteArrayOf(
        event.toByte(),
        (seq and 0xFF).toByte(),
        ((seq shr 8) and 0xFF).toByte(),
        ((seq shr 16) and 0xFF).toByte(),
        ((seq shr 24) and 0xFF).toByte(),
    )

    @Test
    fun recognizes_a_panic_event() {
        assertTrue(PanicButtonBle.isPanic(payload(PanicButtonBle.EVENT_PANIC, 1)))
    }

    @Test
    fun ignores_non_panic_and_empty_payloads() {
        assertFalse(PanicButtonBle.isPanic(byteArrayOf(0x00, 1, 0, 0, 0)))
        assertFalse(PanicButtonBle.isPanic(ByteArray(0)))
    }

    @Test
    fun reads_little_endian_sequence() {
        assertEquals(1L, PanicButtonBle.sequence(payload(PanicButtonBle.EVENT_PANIC, 1)))
        assertEquals(258L, PanicButtonBle.sequence(payload(PanicButtonBle.EVENT_PANIC, 258)))
        assertEquals(4_294_967_295L, PanicButtonBle.sequence(payload(PanicButtonBle.EVENT_PANIC, 4_294_967_295L)))
    }

    @Test
    fun missing_sequence_bytes_return_minus_one() {
        assertEquals(-1L, PanicButtonBle.sequence(byteArrayOf(PanicButtonBle.EVENT_PANIC.toByte())))
    }

    @Test
    fun same_sequence_is_a_duplicate_press() {
        // Simulates the manager's dedupe: the same seq delivered twice fires once.
        var lastSeq = -1L
        var fires = 0
        fun onPayload(value: ByteArray) {
            if (!PanicButtonBle.isPanic(value)) return
            val seq = PanicButtonBle.sequence(value)
            if (seq == lastSeq) return
            lastSeq = seq
            fires++
        }
        onPayload(payload(PanicButtonBle.EVENT_PANIC, 7)) // press
        onPayload(payload(PanicButtonBle.EVENT_PANIC, 7)) // replay on reconnect
        onPayload(payload(PanicButtonBle.EVENT_PANIC, 8)) // next press
        assertEquals(2, fires)
    }
}
