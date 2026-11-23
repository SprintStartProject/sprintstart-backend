package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.delay
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Runs [request], retrying it while Bitbucket is rate limiting or failing.
 *
 * Only `429` and `5xx` are retried: those are the statuses where the same request can plausibly
 * succeed later. A `404` or a `401` is an answer, and retrying it would only delay the caller's
 * error.
 *
 * Bitbucket's `Retry-After` header wins over the local backoff when present, because Bitbucket
 * knows better than we do when the rate limit window closes; the value is capped so a
 * nonsensical header cannot stall a sync indefinitely. Both the header and the backoff are
 * bounded, and the attempt counter is shared, so a request that keeps being rate limited fails
 * with the last `WebClientException` rather than looping.
 *
 * @param requestContext Describes the resource for retry log messages.
 * @param logger Receives the warning logged before each retry.
 * @param request The request to run and possibly re-run.
 * @return The result of the first successful attempt.
 * @throws WebClientException if the request is not retryable or the budget is spent.
 */
internal suspend inline fun <T> withRateLimitRetry(
    requestContext: String,
    logger: Logger,
    crossinline request: suspend () -> T,
): T {
    var attempt = 1

    while (true) {
        try {
            return request()
        } catch (exception: WebClientException) {
            if (!exception.isRetryableHttpStatus() || attempt >= MAX_ATTEMPTS) {
                throw exception
            }
            val waitMillis = exception.retryAfterMillis() ?: backoffMillis(attempt)
            logger.warn(
                "Retrying Bitbucket request '{}' after attempt {} with status {}; delay={}ms",
                requestContext,
                attempt,
                exception.statusCode,
                waitMillis,
            )
            delay(waitMillis)
            attempt++
        }
    }
}

/** Attempts made per request, including the first one. */
private const val MAX_ATTEMPTS = 4

/** Backoff before the second attempt; doubled for each attempt after it. */
private const val INITIAL_BACKOFF_MILLIS = 1_000L

/** Upper bound for both the backoff and a server-supplied `Retry-After`, so a sync cannot park for long. */
private const val MAX_RETRY_DELAY_MILLIS = 60_000L

/**
 * Whether the same request can plausibly succeed later.
 *
 * `429` is Bitbucket's rate limit and `5xx` is Bitbucket failing to answer. Everything else is an
 * answer about the resource.
 */
private fun WebClientException.isRetryableHttpStatus(): Boolean = statusCode == 429 || statusCode in 500..599

/**
 * The delay Bitbucket asked for in its `Retry-After` header, or null when it sent none we understand.
 *
 * The header may be either a number of seconds or an HTTP date, both of which appear in the wild.
 * A value we cannot parse, cannot represent, or that has already passed yields null rather than a
 * guess, and the caller falls back to its own backoff.
 *
 * @return a non-negative delay in milliseconds, capped at the maximum, or null.
 */
private fun WebClientException.retryAfterMillis(): Long? {
    val value = retryAfter?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    value.toLongOrNull()?.let { seconds ->
        return runCatching { Duration.ofSeconds(seconds).toMillis() }
            .getOrNull()
            ?.takeIf { millis -> millis >= 0 }
            ?.coerceAtMost(MAX_RETRY_DELAY_MILLIS)
    }

    return runCatching {
        val retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        Duration.between(Instant.now(), retryAt).toMillis()
    }.getOrNull()
        ?.takeIf { millis -> millis > 0 }
        ?.coerceAtMost(MAX_RETRY_DELAY_MILLIS)
}

/**
 * Exponential backoff for a failed attempt, capped at the maximum.
 *
 * @param failedAttempt The attempt that just failed, counting from one.
 * @return the delay before the next attempt, in milliseconds.
 */
private fun backoffMillis(failedAttempt: Int): Long =
    (INITIAL_BACKOFF_MILLIS shl (failedAttempt - 1)).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
