package com.copiloto.motorista.data.repository

import java.util.Calendar

/** Epoch millis for 00:00 of the current day, used to scope "today" queries. */
fun startOfToday(now: Long = System.currentTimeMillis()): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}
