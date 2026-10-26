package com.viktorolsson.spotter.core.engine

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class)
internal actual fun readTextFile(path: String): String =
    NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: error("Can't read $path")
