package com.viktorolsson.spotter.core.data.repository


/**
 * Runs [block] with the phone's time zone set to Europe/Stockholm (what
 * `TimeZone.currentSystemDefault()` reads on the JVM), then puts the old one back.
 */
inline fun <T> inStockholm(block: () -> T): T {
    val previous = java.util.TimeZone.getDefault()
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Stockholm"))
    try {
        return block()
    } finally {
        java.util.TimeZone.setDefault(previous)
    }
}

/**
 * Moves the clock forward to the next 22:30 UTC: half past midnight in Stockholm in summer
 * time, when the UTC date is still the day before.
 */
fun MutableClock.advanceToHalfPastMidnightInStockholm() {
    val secondsIntoUtcDay = now().epochSeconds.mod(86_400L)
    val target = 22 * 3_600L + 30 * 60L
    advanceSeconds((target - secondsIntoUtcDay).mod(86_400L))
    check(now().epochSeconds.mod(86_400L) == target) { "now is ${now()}" }
}

