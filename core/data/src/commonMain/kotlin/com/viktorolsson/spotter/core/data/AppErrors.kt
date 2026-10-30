package com.viktorolsson.spotter.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** What failed, in the person's terms; the app shell turns each into a message. */
enum class Failure {
    /** A change wasn't saved (a set, a note, a plan edit). */
    SAVE,

    /** Something couldn't be loaded or done, nothing more specific to say. */
    ACTION,

    /** A tapped sign-in or password-reset link didn't sign in. */
    SIGN_IN_LINK,
}

/**
 * The one place a failure goes, so none is silent: it's logged (and handed to crash reporting
 * once that's added), and [Failure]s the person should know about reach [messages], which the
 * app shell shows as a snackbar. One per process, from the DataGraph.
 */
class AppErrors {
    private val _messages = MutableSharedFlow<Failure>(extraBufferCapacity = 8)

    /** Failures to tell the person about, as they happen. */
    val messages: SharedFlow<Failure> = _messages.asSharedFlow()

    /** [error] made [failure] fail: logged, and shown to the person. */
    fun failed(failure: Failure, error: Throwable) {
        _messages.tryEmit(failure)
        noticed(error, failure.name)
    }

    /** Logged (and reported) without a message, for failures the person can't act on. */
    fun noticed(error: Throwable, context: String) {
        // Class names only: exception messages can carry payloads, tokens or email addresses.
        logWarn(TAG, "$context: ${error::class.simpleName}")
    }

    /** For a scope that must never crash the app: anything uncaught becomes [Failure.ACTION]. */
    val handler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, error -> failed(Failure.ACTION, error) }

    private companion object {
        const val TAG = "AppErrors"
    }
}

/**
 * [launch], but a failure in [block] is reported through [errors] as [failure] (logged and
 * shown) instead of crashing the app or vanishing. Cancellation passes through as usual.
 */
fun CoroutineScope.launchReporting(errors: AppErrors, failure: Failure = Failure.SAVE, block: suspend CoroutineScope.() -> Unit): Job =
    launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.failed(failure, e)
        }
    }
