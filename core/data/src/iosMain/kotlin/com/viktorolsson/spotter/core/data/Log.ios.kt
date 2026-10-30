package com.viktorolsson.spotter.core.data

import platform.Foundation.NSLog

internal actual fun logDebug(tag: String, message: String) {
    // Debug logging is off on iOS; warnings are enough to follow sync in Console.app.
}

actual fun logWarn(tag: String, message: String) {
    // One preformatted string as the format, with % escaped. Passing Kotlin strings through
    // NSLog's C varargs ("%@: %@", tag, message) crashed the process (LogTest).
    NSLog("$tag: $message".replace("%", "%%"))
}
