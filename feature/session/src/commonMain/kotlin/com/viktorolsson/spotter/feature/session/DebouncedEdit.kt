package com.viktorolsson.spotter.feature.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The latest keypad edit, handed to [write] after [delayMs] without a newer one
 * (or straight away on [commit]). Only the latest edit is kept, and writes never overlap:
 * [commit] returns only once any write already running has finished.
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
    private val writing = Mutex()

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

    /**
     * Writes the queued edit now, after any write already running. Without the wait, a tick
     * made just after the pause ran alongside that write and one of them was lost.
     */
    suspend fun commit() {
        job?.cancel()
        job = null
        writing.withLock {
            val edit = pending ?: return
            pending = null
            // A write that has started finishes, even if the screen goes meanwhile.
            withContext(NonCancellable) { write(edit) }
        }
    }

    /** Drops the queued edit unwritten and returns it. A write already running still finishes. */
    fun take(): T? {
        job?.cancel()
        job = null
        return pending.also { pending = null }
    }
}
