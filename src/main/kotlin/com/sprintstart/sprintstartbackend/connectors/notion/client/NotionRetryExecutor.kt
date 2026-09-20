package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.NotionRetryConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException
import kotlin.math.roundToLong
import kotlin.random.Random

internal fun interface NotionRetrySleeper {
    suspend fun sleep(delay: Duration)
}

internal fun interface NotionRetryClock {
    fun now(): Instant
}

internal class NotionRetryExecutor(
    private val config: NotionRetryConfig,
    private val sleeper: NotionRetrySleeper,
    private val clock: NotionRetryClock,
    private val throttle: NotionRequestThrottle,
    private val random: () -> Double = { Random.nextDouble() },
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(config.maxAttempts > 0) { "Notion retry maxAttempts must be positive" }
        require(!config.initialDelay.isNegative) { "Notion retry initialDelay must not be negative" }
        require(config.maxDelay >= Duration.ofMillis(1)) { "Notion retry maxDelay must be at least 1ms" }
        require(config.initialDelay <= config.maxDelay) { "Notion initialDelay must not exceed maxDelay" }
        require(config.multiplier >= 1.0 && config.multiplier.isFinite()) {
            "Notion retry multiplier must be finite and at least 1.0"
        }
        require(!config.jitter.isNegative && config.jitter <= config.maxDelay) {
            "Notion retry jitter must be between zero and maxDelay"
        }
    }

    suspend fun <T> execute(requestContext: String, request: suspend () -> T): T {
        var attempt = 1
        while (true) {
            currentCoroutineContext().ensureActive()
            throttle.awaitPermit(requestContext)
            try {
                return request()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw exception
            } catch (exception: WebClientException) {
                val wait = retryDelayOrThrow(exception, requestContext, attempt)
                logRetry(requestContext, attempt, exception.statusCode, wait)
                sleeper.sleep(wait)
            } catch (exception: IOException) {
                if (exception is SSLException || attempt >= config.maxAttempts) {
                    throw NotionTransportException(requestContext, attempt, exception !is SSLException)
                }
                val wait = configuredDelay(attempt)
                logRetry(requestContext, attempt, null, wait)
                sleeper.sleep(wait)
            } catch (@Suppress("SwallowedException") exception: SerializationException) {
                throw NotionInvalidResponseException(requestContext, attempt)
            }
            attempt++
        }
    }

    private fun retryDelayOrThrow(
        exception: WebClientException,
        requestContext: String,
        attempt: Int,
    ): Duration {
        val retryable = exception.statusCode in RETRYABLE_STATUSES
        if (!retryable) {
            throw exception.toSafeNotionException(requestContext, attempt, retryExhausted = false)
        }
        val fallback = configuredDelay(attempt)
        val serverDelay = exception.retryAfter?.let { parseRetryAfter(it) }
        val wait = maxOf(fallback, serverDelay ?: Duration.ZERO)
        if (exception.statusCode == 429 || exception.statusCode == 529) {
            throttle.deferFor(wait)
        }
        if (attempt >= config.maxAttempts || wait > config.maxDelay) {
            throw exception.toSafeNotionException(requestContext, attempt, retryExhausted = true)
        }
        return wait
    }

    private fun parseRetryAfter(value: String): Duration? {
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && trimmed.all { it in '0'..'9' }) {
            return Duration.ofSeconds(trimmed.toLongOrNull() ?: Long.MAX_VALUE)
        }
        return try {
            val retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            Duration.between(clock.now(), retryAt).takeIf { !it.isNegative }
        } catch (@Suppress("SwallowedException") exception: DateTimeException) {
            null
        }
    }

    private fun configuredDelay(failedAttempt: Int): Duration {
        val maximumMillis = config.maxDelay.toMillis()
        var millis = config.initialDelay.toMillis().toDouble()
        repeat(failedAttempt - 1) {
            millis = (millis * config.multiplier).coerceAtMost(maximumMillis.toDouble())
        }
        val jitterMillis = config.jitter.toMillis() * random()
        return Duration.ofMillis((millis + jitterMillis).roundToLong().coerceAtMost(maximumMillis))
    }

    private fun logRetry(requestContext: String, attempt: Int, status: Int?, wait: Duration) {
        logger.warn(
            "Retrying Notion operation '{}' after attempt {} with status {}; delay={}ms",
            requestContext,
            attempt,
            status,
            wait.toMillis(),
        )
    }

    private companion object {
        val RETRYABLE_STATUSES = setOf(429, 500, 502, 503, 504, 529)
    }
}
