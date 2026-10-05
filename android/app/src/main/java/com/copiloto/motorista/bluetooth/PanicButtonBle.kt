package com.copiloto.motorista.bluetooth

import java.util.UUID

/**
 * BLE contract between the ESP32 panic button (GATT peripheral) and the app
 * (GATT central). The ESP32 advertises [SERVICE_UUID] and, on a button press,
 * notifies [PANIC_CHAR_UUID] with `[eventType, seq(uint32 LE)]`. The app tracks
 * the sequence number so a reconnect/replay of the same press fires only once.
 *
 * The matching firmware sketch is in `esp32/panic_button/` at the repo root.
 */
object PanicButtonBle {
    val SERVICE_UUID: UUID = UUID.fromString("6b2f0001-5f3a-4b3e-9a1e-2d4c6f8a0b01")
    val PANIC_CHAR_UUID: UUID = UUID.fromString("6b2f0002-5f3a-4b3e-9a1e-2d4c6f8a0b02")

    /** Standard Client Characteristic Configuration Descriptor (enables notify). */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Name the ESP32 advertises, so the pairing scan can recognize it. */
    const val DEVICE_NAME = "Copiloto-Panico"

    /** First byte of a notification payload: a deliberate panic press. */
    const val EVENT_PANIC: Int = 0x01

    /** True when the payload is a panic event (`[EVENT_PANIC, seq…]`). */
    fun isPanic(value: ByteArray): Boolean =
        value.isNotEmpty() && (value[0].toInt() and 0xFF) == EVENT_PANIC

    /**
     * The little-endian uint32 sequence number in bytes 1..4, or -1 when absent.
     * The app ignores a payload whose sequence equals the last one handled, so a
     * reconnect that re-delivers the final notification does not re-fire.
     */
    fun sequence(value: ByteArray): Long {
        if (value.size < 5) return -1L
        var seq = 0L
        for (i in 0 until 4) {
            seq = seq or ((value[1 + i].toLong() and 0xFF) shl (8 * i))
        }
        return seq
    }
}
