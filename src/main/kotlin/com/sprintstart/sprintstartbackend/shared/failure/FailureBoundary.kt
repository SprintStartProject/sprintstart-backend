package com.sprintstart.sprintstartbackend.shared.failure

import kotlinx.coroutines.CancellationException

/**
 * Runs [block] and, when it fails with anything but a cancellation, hands the failure to [onFailure]
 * and rethrows it.
 *
 * For the edge of a unit of work that must announce how it ended, such as a fetch that publishes a
 * failed event. A cancellation abandons the work rather than failing it, so it is rethrown untouched
 * and never reported.
 */
inline fun <T> reportingFailure(onFailure: (Exception) -> Unit, block: () -> T): T =
    catching(cancellationToo = false, handler = { failure -> onFailure(failure).let { throw failure } }, block = block)

/**
 * Runs [block] and, when it fails with anything but a cancellation, returns what [fallback] makes of
 * the failure.
 *
 * For work whose failure is logged or absorbed instead of surfaced, such as a listener that must not
 * take the publisher down. A cancellation is rethrown, so a stopped caller stays stopped.
 */
inline fun <T> recovering(fallback: (Exception) -> T, block: () -> T): T =
    catching(cancellationToo = false, handler = fallback, block = block)

/**
 * Runs [block] and, when it fails for any reason, a cancellation included, runs [cleanup] and rethrows.
 *
 * For work that leaves something half-done on every way out, such as a streamed reply that must keep
 * what the reader already saw whether the turn failed or was stopped.
 */
inline fun <T> afterFailure(cleanup: () -> Unit, block: () -> T): T =
    catching(cancellationToo = true, handler = { failure -> cleanup().let { throw failure } }, block = block)

/**
 * Runs [block] and, when it fails for any reason, a cancellation included, throws what [translate]
 * makes of the failure.
 *
 * For work whose caller waits on a collective outcome, such as awaiting a group of jobs, where a
 * stopped member still has to leave the group's state settled before the failure moves on.
 */
inline fun <T> translatingFailure(translate: (Exception) -> Throwable, block: () -> T): T =
    catching(cancellationToo = true, handler = { failure -> throw translate(failure) }, block = block)

/** The one place a generic failure is caught on purpose; the functions above differ only in what they do next. */
@PublishedApi
internal inline fun <T> catching(cancellationToo: Boolean, handler: (Exception) -> T, block: () -> T): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        if (cancellationToo) handler(cancellation) else throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
        handler(failure)
    }
