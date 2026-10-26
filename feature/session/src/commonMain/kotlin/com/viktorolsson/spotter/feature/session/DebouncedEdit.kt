package com.viktorolsson.spotter.feature.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The latest keypad edit, handed to [write] after [delayMs] without a newer one
 * (or straight away on [commit]). Only the latest edit is kept.
 */
internal class DebouncedEdit<T : Any>(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val write: suspend (T) -> Unit,
) {
    /** The edit not yet handed to [write], if any. */
    var pending: T? = null
        private set
    private var job: Job? = null

    /** Queues [edit], replacing any queued one; written after the pause. */
    fun submit(edit: T) {
        pending = edit
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            // commit() cancels [job]: let go of it first, or the write cancels itself mid-way.
            job = null
            commit()
        }
    }

    /** Writes the queued edit now. */
    suspend fun commit() {
        job?.cancel()
        job = null
        val edit = pending ?: return
        pending = null
        write(edit)
    }

    /** Drops the queued edit unwritten and returns it. */
    fun take(): T? {
        job?.cancel()
        job = null
        return pending.also { pending = null }
    }
}
