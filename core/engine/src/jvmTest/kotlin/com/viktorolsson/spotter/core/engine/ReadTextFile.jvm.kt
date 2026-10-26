package com.viktorolsson.spotter.core.engine

import java.io.File

internal actual fun readTextFile(path: String): String = File(path).readText()
