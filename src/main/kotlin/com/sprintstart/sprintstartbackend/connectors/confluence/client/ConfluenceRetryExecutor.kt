package com.sprintstart.sprintstartbackend.connectors.confluence.client

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.ConfluenceRetryConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException
import kotlin.math.min
import kotlin.math.roundToLong

internal fun interface ConfluenceRetrySleeper {
    suspend fun sleep(delay: Duration)
}

internal fun interface ConfluenceRetryClock {
    fun now(): Instant
}

/** Executes one Confluence HTTP request with bounded, explicitly classified retries. */
internal class ConfluenceRetryExecutor(
    private val config: ConfluenceRetryConfig,
    private val sleeper: ConfluenceRetrySleeper,
    private val clock: ConfluenceRetryClock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(config.maxAttempts > 0) { "Confluence retry maxAttempts must be positive" }
        require(!config.initialDelay.isNegative) { "Confluence retry initialDelay must not be negative" }
        require(!config.maxDelay.isNegative && !config.maxDelay.isZero) {
            "Confluence retry maxDelay must be positive"
        }
        require(config.initialDelay <= config.maxDelay) {
            "Confluence retry initialDelay must not exceed maxDelay"
        }
        require(config.multiplier >= 1.0 && config.multiplier.isFinite()) {
            "Confluence retry multiplier must be finite and at least 1.0"
        }
    }

    suspend fun <T> execute(
        requestContext: String,
        request: suspend () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return request()
            } catch (exception: CancellationException) {
                propagateCancellation(exception)
            } catch (exception: InterruptedException) {
                propagateInterruption(exception)
            } catch (exception: WebClientException) {
                val retryDelay = retryDelayOrThrow(exception, requestContext, attempt)
                logRetry(requestContext, attempt, exception.statusCode, retryDelay)
                sleeper.sleep(retryDelay)
                attempt++
            } catch (exception: IOException) {
                val retryDelay = transportRetryDelayOrThrow(exception, requestContext, attempt)
                logRetry(requestContext, attempt, null, retryDelay)
                sleeper.sleep(retryDelay)
                attempt++
            }
        }
    }

    private fun retryDelayOrThrow(
        exception: WebClientException,
        requestContext: String,
        attempt: Int,
    ): Duration {
        val retryable = exception.statusCode.isRetryableHttpStatus()
        if (!retryable || attempt >= config.maxAttempts) {
            throw exception.toSafeConfluenceException(
                requestContext = requestContext,
                attempts = attempt,
                retryExhausted = retryable,
            )
        }
        return retryDelay(exception.retryAfter, attempt)
            ?: throw exception.toSafeConfluenceException(requestContext, attempt, retryExhausted = true)
    }

    private fun transportRetryDelayOrThrow(
        exception: IOException,
        requestContext: String,
        attempt: Int,
    ): Duration {
        val retryable = exception.isRetryableTransportFailure()
        if (!retryable || attempt >= config.maxAttempts) {
            throw ConfluenceTransportException(
                requestContext = requestContext,
                attempts = attempt,
                retryExhausted = retryable,
            )
        }
        return configuredDelay(attempt)
    }

    private fun retryDelay(retryAfter: String?, failedAttempt: Int): Duration? {
        if (retryAfter == null) {
            return configuredDelay(failedAttempt)
        }
        val serverDelay = parseRetryAfter(retryAfter) ?: return configuredDelay(failedAttempt)
        return serverDelay.takeIf { delay -> delay <= config.maxDelay }
    }

    private fun parseRetryAfter(value: String): Duration? {
        val trimmed = value.trim()
        val seconds = trimmed.toLongOrNull()
        if (seconds != null) {
            if (seconds < 0) return null
            return try {
                Duration.ofSeconds(seconds)
            } catch (@Suppress("SwallowedException") exception: ArithmeticException) {
                null
            }
        }
        return try {
            val retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            Duration.between(clock.now(), retryAt).takeIf { delay -> delay.isPositive }
        } catch (@Suppress("SwallowedException") exception: DateTimeException) {
            null
        }
    }

    private fun configuredDelay(failedAttempt: Int): Duration {
        var delayMillis = config.initialDelay.toMillis()
        repeat(failedAttempt - 1) {
            val multiplied = delayMillis.toDouble() * config.multiplier
            delayMillis = if (multiplied >= config.maxDelay.toMillis()) {
                config.maxDelay.toMillis()
            } else {
                min(multiplied.roundToLong(), config.maxDelay.toMillis())
            }
        }
        return Duration.ofMillis(delayMillis)
    }

    private fun logRetry(
        requestContext: String,
        failedAttempt: Int,
        httpStatus: Int?,
        retryDelay: Duration,
    ) {
        logger.warn(
            "Retrying Confluence operation '{}' after attempt {} with status {}; delay={}ms",
            requestContext,
            failedAttempt,
            httpStatus,
            retryDelay.toMillis(),
        )
    }
}

private fun propagateCancellation(exception: CancellationException): Nothing {
    throw exception
}

private fun propagateInterruption(exception: InterruptedException): Nothing {
    Thread.currentThread().interrupt()
    throw exception
}

@Configuration
internal class ConfluenceRetryConfiguration {
    @Bean
    fun confluenceRetrySleeper(): ConfluenceRetrySleeper {
        return ConfluenceRetrySleeper { duration -> delay(duration.toMillis()) }
    }

    @Bean
    fun confluenceRetryClock(): ConfluenceRetryClock {
        return ConfluenceRetryClock { Instant.now() }
    }

    @Bean
    fun confluenceRetryExecutor(
        applicationConfig: ApplicationConfig,
        sleeper: ConfluenceRetrySleeper,
        clock: ConfluenceRetryClock,
    ): ConfluenceRetryExecutor {
        return ConfluenceRetryExecutor(applicationConfig.confluence.retry, sleeper, clock)
    }
}

private fun Int.isRetryableHttpStatus(): Boolean = this == 429 || this in 500..599

private fun IOException.isRetryableTransportFailure(): Boolean {
    return this !is SSLException
}
