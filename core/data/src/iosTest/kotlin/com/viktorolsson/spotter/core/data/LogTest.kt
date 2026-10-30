package com.viktorolsson.spotter.core.data

import kotlin.test.Test

/** logWarn used to pass Kotlin strings to NSLog's C varargs, which crashed the process. */
class LogTest {
    @Test
    fun loggingAWarningDoesNotCrash() {
        logWarn("Test", "100% done: a message with a percent sign")
        logDebug("Test", "ignored")
    }
}
