package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.NotionThrottleConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class NotionRequestThrottleTest {
    @Test
    fun `concurrent sources receive spaced permits from one queue`() = runTest {
        val throttle = NotionRequestThrottle(
            NotionThrottleConfig(),
            NotionRetrySleeper { delay(it.toMillis()) },
            NotionRetryClock { Instant.EPOCH.plusMillis(testScheduler.currentTime) },
        )
        val starts = mutableListOf<Long>()

        repeat(4) {
            launch {
                throttle.awaitPermit("source")
                starts += testScheduler.currentTime
            }
        }
        advanceUntilIdle()

        assertThat(starts).containsExactly(0L, 334L, 668L, 1002L)
    }

    @Test
    fun `new cooldown is respected by a request already waiting for its permit`() = runTest {
        val throttle = NotionRequestThrottle(
            NotionThrottleConfig(),
            NotionRetrySleeper { delay(it.toMillis()) },
            NotionRetryClock { Instant.EPOCH.plusMillis(testScheduler.currentTime) },
        )
        throttle.awaitPermit("first")
        var start = -1L
        launch {
            throttle.awaitPermit("second")
            start = testScheduler.currentTime
        }
        runCurrent()

        throttle.deferFor(Duration.ofSeconds(2))
        advanceUntilIdle()

        assertThat(start).isEqualTo(2000L)
    }

    @Test
    fun `cancelling a waiter releases queue and does not reserve a future slot`() = runTest {
        val throttle = NotionRequestThrottle(
            NotionThrottleConfig(),
            NotionRetrySleeper { delay(it.toMillis()) },
            NotionRetryClock { Instant.EPOCH.plusMillis(testScheduler.currentTime) },
        )
        throttle.awaitPermit("first")
        val waiter = launch { throttle.awaitPermit("cancelled") }
        runCurrent()
        waiter.cancelAndJoin()

        throttle.awaitPermit("next")

        assertThat(testScheduler.currentTime).isEqualTo(334L)
    }

    @Test
    fun `does not shorten existing cooldown and resumes once it expires`() = runTest {
        val throttle = NotionRequestThrottle(
            NotionThrottleConfig(),
            NotionRetrySleeper { delay(it.toMillis()) },
            NotionRetryClock { Instant.EPOCH.plusMillis(testScheduler.currentTime) },
        )
        throttle.deferFor(Duration.ofSeconds(60))
        throttle.deferFor(Duration.ofSeconds(1))

        assertThrows<NotionRequestDeferredException> { throttle.awaitPermit("too soon") }
        assertThat(testScheduler.currentTime).isZero()
        delay(60_000)
        throttle.awaitPermit("after cooldown")
        assertThat(testScheduler.currentTime).isEqualTo(60_000L)
    }
}
