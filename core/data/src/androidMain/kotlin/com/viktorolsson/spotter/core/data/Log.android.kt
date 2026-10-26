package com.viktorolsson.spotter.core.data

import android.util.Log

internal actual fun logDebug(tag: String, message: String) {
    if (Log.isLoggable(tag, Log.DEBUG)) Log.d(tag, message)
}

internal actual fun logWarn(tag: String, message: String) {
    Log.w(tag, message)
}
