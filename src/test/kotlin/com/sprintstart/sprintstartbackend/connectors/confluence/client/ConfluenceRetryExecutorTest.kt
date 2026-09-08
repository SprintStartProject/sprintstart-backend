package com.sprintstart.sprintstartbackend.connectors.confluence.client

import com.sprintstart.sprintstartbackend.ConfluenceRetryConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.ConnectException
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLHandshakeException

internal class ConfluenceRetryExecutorTest {
    @Test
    fun `temporary transport failure retries and succeeds without real waiting`() = runTest {
        val delays = mutableListOf<Duration>()
        val executor = executor(delays)
        var attempts = 0

        val result = executor.execute("retrieving page 100") {
            attempts++
            if (attempts == 1) throw ConnectException("sensitive transport detail")
            "ok"
        }

        assertThat(result).isEqualTo("ok")
        assertThat(attempts).isEqualTo(2)
        assertThat(delays).containsExactly(Duration.ofMillis(100))
    }

    @Test
    fun `generic temporary IO failure is retried`() = runTest {
        val delays = mutableListOf<Duration>()
        val executor = executor(delays)
        var attempts = 0

        val result = executor.execute("retrieving page 100") {
            attempts++
            if (attempts == 1) throw IOException("temporary upstream detail")
            "ok"
        }

        assertThat(result).isEqualTo("ok")
        assertThat(attempts).isEqualTo(2)
        assertThat(delays).containsExactly(Duration.ofMillis(100))
    }

    @Test
    fun `temporary transport failure stops at total maxAttempts`() = runTest {
        val delays = mutableListOf<Duration>()
        val executor = executor(delays)
        var attempts = 0

        val exception = assertThrows<ConfluenceTransportException> {
            executor.execute("retrieving page 100") {
                attempts++
                throw ConnectException("token-like-sensitive-detail")
            }
        }

        assertThat(attempts).isEqualTo(3)
        assertThat(exception.attempts).isEqualTo(3)
        assertThat(exception.retryExhausted).isTrue()
        assertThat(exception.message).doesNotContain("token-like-sensitive-detail")
        assertThat(exception.cause).isNull()
        assertThat(delays).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200))
    }

    @Test
    fun `TLS validation failure is sanitized and not retried`() = runTest {
        val delays = mutableListOf<Duration>()
        val executor = executor(delays)
        var attempts = 0

        val exception = assertThrows<ConfluenceTransportException> {
            executor.execute("validating connection") {
                attempts++
                throw SSLHandshakeException("certificate secret")
            }
        }

        assertThat(attempts).isEqualTo(1)
        assertThat(exception.retryExhausted).isFalse()
        assertThat(exception.message).doesNotContain("certificate secret")
        assertThat(delays).isEmpty()
    }

    @Test
    fun `cancellation propagates immediately without retry`() = runTest {
        val delays = mutableListOf<Duration>()
        val executor = executor(delays)
        val cancellation = CancellationException("cancel now")

        val thrown = assertThrows<CancellationException> {
            executor.execute("retrieving pages") { throw cancellation }
        }

        assertThat(thrown).isSameAs(cancellation)
        assertThat(delays).isEmpty()
    }

    @Test
    fun `thread interruption propagates immediately and restores interrupt flag`() = runTest {
        val delays = mutableListOf<Duration>()
        val executor = executor(delays)

        try {
            assertThrows<InterruptedException> {
                executor.execute("retrieving pages") { throw InterruptedException("interrupted") }
            }
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            assertThat(delays).isEmpty()
        } finally {
            Thread.interrupted()
        }
    }

    private fun executor(delays: MutableList<Duration>): ConfluenceRetryExecutor {
        return ConfluenceRetryExecutor(
            config = ConfluenceRetryConfig(
                maxAttempts = 3,
                initialDelay = Duration.ofMillis(100),
                maxDelay = Duration.ofSeconds(30),
                multiplier = 2.0,
            ),
            sleeper = ConfluenceRetrySleeper { delay -> delays += delay },
            clock = ConfluenceRetryClock { Instant.parse("2026-10-21T07:27:50Z") },
        )
    }
}
