package com.shifurace.util

import kotlin.math.abs

object TimeFormat {
    /** 74_221 ms -> "1:14.221" ; acima de 1h -> "1:02:34.221" */
    fun race(ms: Long): String {
        if (ms < 0) return "-:--.---"
        val totalSeconds = ms / 1000
        val millis = ms % 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
        } else {
            "%d:%02d.%03d".format(minutes, seconds, millis)
        }
    }

    /** Diferença com sinal: -412 -> "-0.412", 1842 -> "+1.842", 65_000 -> "+1:05.000" */
    fun delta(ms: Long): String {
        val sign = if (ms < 0) "-" else "+"
        val a = abs(ms)
        return if (a >= 60_000) sign + race(a) else "%s%d.%03d".format(sign, a / 1000, a % 1000)
    }
}
