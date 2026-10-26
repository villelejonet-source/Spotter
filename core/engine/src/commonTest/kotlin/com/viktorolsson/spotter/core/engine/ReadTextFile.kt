package com.viktorolsson.spotter.core.engine

/** Reads a file from the host's disk (the iOS simulator shares the Mac's file system). */
internal expect fun readTextFile(path: String): String
