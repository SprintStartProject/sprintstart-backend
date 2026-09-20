package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.NotionRetryConfig
import com.sprintstart.sprintstartbackend.NotionThrottleConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLException

internal class NotionRetryExecutorTest {
    private var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val waits = mutableListOf<Duration>()
    private val clock = NotionRetryClock { now }
    private val sleeper = NotionRetrySleeper { wait ->
        waits += wait
        now = now.plus(wait)
    }
    private val throttle = NotionRequestThrottle(
        NotionThrottleConfig(minInterval = Duration.ofMillis(10)),
        sleeper,
        clock,
    )

    private fun executor(
        config: NotionRetryConfig = NotionRetryConfig(
            maxAttempts = 3,
            initialDelay = Duration.ofMillis(100),
            jitter = Duration.ZERO,
        ),
        retrySleeper: NotionRetrySleeper = sleeper,
        random: () -> Double = { 0.0 },
    ): NotionRetryExecutor {
        return NotionRetryExecutor(config, retrySleeper, clock, throttle, random)
    }

    private fun failure(status: Int, retryAfter: String? = null): WebClientException {
        return WebClientException(status, "secret-body", "secret-message", retryAfter)
    }

    @ParameterizedTest
    @ValueSource(ints = [429, 500, 502, 503, 504, 529])
    fun `retries classified temporary errors with increasing delays`(status: Int) = runTest {
        var attempts = 0

        val result = executor().execute("reading page") {
            attempts++
            if (attempts < 3) throw failure(status)
            "success"
        }

        assertThat(result).isEqualTo("success")
        assertThat(attempts).isEqualTo(3)
        assertThat(waits).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200))
    }

    @Test
    fun `HTTP date Retry-After uses injected clock`() = runTest {
        var attempts = 0
        executor().execute("reading page") {
            if (++attempts == 1) throw failure(429, "Thu, 01 Jan 2026 00:00:05 GMT")
        }

        assertThat(waits).containsExactly(Duration.ofSeconds(5))
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid", "-1", "Wed, 31 Dec 2025 23:59:00 GMT"])
    fun `invalid or past Retry-After falls back to backoff`(retryAfter: String) = runTest {
        var attempts = 0
        executor().execute("reading page") {
            if (++attempts == 1) throw failure(529, retryAfter)
        }

        assertThat(waits).containsExactly(Duration.ofMillis(100))
    }

    @ParameterizedTest
    @ValueSource(strings = ["31", "9223372036854775807", "999999999999999999999999"])
    fun `does not shorten a server wait beyond budget even on numeric overflow`(retryAfter: String) = runTest {
        var attempts = 0
        val error = assertThrows<NotionExternalServiceException> {
            executor().execute("reading page") {
                attempts++
                throw failure(429, retryAfter)
            }
        }

        assertThat(error.retryExhausted).isTrue()
        assertThat(error.attempts).isEqualTo(1)
        assertThat(error.cause).isNull()
        assertThat(attempts).isEqualTo(1)
        assertThat(waits).isEmpty()
        assertThrows<NotionRequestDeferredException> { throttle.awaitPermit("another source") }
    }

    @ParameterizedTest
    @ValueSource(ints = [429, 529])
    fun `exhaustion preserves cooldown without extra attempts or leaked upstream data`(status: Int) = runTest {
        var attempts = 0
        val error = assertThrows<NotionExternalServiceException> {
            executor().execute("reading page") {
                attempts++
                throw failure(status, "1")
            }
        }

        assertThat(attempts).isEqualTo(3)
        assertThat(error.attempts).isEqualTo(3)
        assertThat(error.retryExhausted).isTrue()
        assertThat(error.stackTraceToString()).doesNotContain("secret-body", "secret-message")
        assertThat(error.cause).isNull()
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1))
        throttle.awaitPermit("another source")
        assertThat(waits.last()).isEqualTo(Duration.ofSeconds(1))
    }

    @Test
    fun `jitter and exponential delay stay within configured maximum`() = runTest {
        var attempts = 0
        val config = NotionRetryConfig(
            maxAttempts = 4,
            initialDelay = Duration.ofMillis(100),
            maxDelay = Duration.ofMillis(250),
            jitter = Duration.ofMillis(100),
        )

        executor(config, random = { 0.5 }).execute("reading page") {
            if (++attempts < 4) throw failure(503)
        }

        assertThat(waits).containsExactly(
            Duration.ofMillis(150),
            Duration.ofMillis(250),
            Duration.ofMillis(250),
        )
    }

    @Test
    fun `cancellation from request propagates unchanged without retry`() = runTest {
        val cancellation = CancellationException("cancelled")
        var attempts = 0

        val error = assertThrows<CancellationException> {
            executor().execute("reading page") {
                attempts++
                throw cancellation
            }
        }

        assertThat(error).isSameAs(cancellation)
        assertThat(attempts).isEqualTo(1)
        assertThat(waits).isEmpty()
    }

    @Test
    fun `cancellation during retry sleep does not send another request`() = runTest {
        var attempts = 0
        val cancellation = CancellationException("cancelled while sleeping")

        val error = assertThrows<CancellationException> {
            executor(retrySleeper = NotionRetrySleeper { throw cancellation }).execute("reading page") {
                attempts++
                throw failure(503)
            }
        }

        assertThat(error).isSameAs(cancellation)
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `interruption restores flag and is not retried`() = runTest {
        val interruption = InterruptedException("stop")
        try {
            assertThat(
                assertThrows<InterruptedException> {
                    executor().execute("reading page") { throw interruption }
                },
            ).isSameAs(interruption)
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            assertThat(waits).isEmpty()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `transport failures retry but terminal exception drops raw cause`() = runTest {
        var attempts = 0
        val error = assertThrows<NotionTransportException> {
            executor().execute("reading page") {
                attempts++
                throw IOException("secret-network-detail")
            }
        }

        assertThat(attempts).isEqualTo(3)
        assertThat(error.retryExhausted).isTrue()
        assertThat(error.cause).isNull()
        assertThat(error.message).doesNotContain("secret-network-detail")
    }

    @Test
    fun `TLS errors are terminal`() = runTest {
        val error = assertThrows<NotionTransportException> {
            executor().execute("reading page") { throw SSLException("secret-certificate") }
        }

        assertThat(error.attempts).isEqualTo(1)
        assertThat(error.retryExhausted).isFalse()
        assertThat(error.cause).isNull()
        assertThat(waits).isEmpty()
    }

    @Test
    fun `rejects invalid retry configuration`() {
        val invalid = listOf(
            NotionRetryConfig(maxAttempts = 0),
            NotionRetryConfig(initialDelay = Duration.ofSeconds(-1)),
            NotionRetryConfig(maxDelay = Duration.ZERO),
            NotionRetryConfig(initialDelay = Duration.ofSeconds(31)),
            NotionRetryConfig(multiplier = 0.5),
            NotionRetryConfig(multiplier = Double.NaN),
            NotionRetryConfig(jitter = Duration.ofSeconds(-1)),
        )
        invalid.forEach { config -> assertThrows<IllegalArgumentException> { executor(config) } }
    }
}
