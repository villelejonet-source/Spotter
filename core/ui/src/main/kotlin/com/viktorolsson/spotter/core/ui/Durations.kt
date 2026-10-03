package com.viktorolsson.spotter.core.ui

import java.time.Duration

/** 0:45, 12:05, 1:02:09 */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun formatClock(duration: Duration): String = formatClock(duration.seconds)

/** Rest lengths offered wherever a rest time is chosen. */
val RestOptionsSeconds = listOf(30, 45, 60, 75, 90, 120, 150, 180, 240, 300)
