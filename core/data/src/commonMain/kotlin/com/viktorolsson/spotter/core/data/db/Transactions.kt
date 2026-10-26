package com.viktorolsson.spotter.core.data.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Room's Android-only `withTransaction` for shared code: one immediate (write-locked)
 * transaction on the writer connection. DAO calls made inside [block] run on that connection,
 * so they commit or roll back together.
 */
suspend fun <R> SpotterDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
