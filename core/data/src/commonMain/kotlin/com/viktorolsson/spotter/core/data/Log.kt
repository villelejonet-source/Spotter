package com.viktorolsson.spotter.core.data

// Logcat on Android, the unified log on iOS. Messages carry class names and table names only:
// no payloads, tokens or email addresses.

/** Only written when debug logging is on for [tag] (`adb shell setprop log.tag.<tag> DEBUG`). */
internal expect fun logDebug(tag: String, message: String)

expect fun logWarn(tag: String, message: String)
