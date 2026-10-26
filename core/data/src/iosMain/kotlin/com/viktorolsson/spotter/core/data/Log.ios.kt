package com.viktorolsson.spotter.core.data

import platform.Foundation.NSLog

internal actual fun logDebug(tag: String, message: String) {
    // Debug logging is off on iOS; warnings are enough to follow sync in Console.app.
}

actual fun logWarn(tag: String, message: String) {
    NSLog("%@: %@", tag, message)
}
