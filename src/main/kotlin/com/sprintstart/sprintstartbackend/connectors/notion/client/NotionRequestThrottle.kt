package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.NotionThrottleConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

internal class NotionRequestThrottle(
    private val config: NotionThrottleConfig,
    private val sleeper: NotionRetrySleeper,
    private val clock: NotionRetryClock,
) {
    private val queue = Mutex()
    private val cooldownUntil = AtomicReference(Instant.MIN)
    private var nextRequestAt: Instant = Instant.MIN

    init {
        require(config.minInterval >= Duration.ofMillis(1)) { "Notion request interval must be at least 1ms" }
        require(config.maxWait >= config.minInterval) { "Notion max wait must cover the request interval" }
    }

    suspend fun awaitPermit(requestContext: String) {
        val deadline = clock.now().plus(config.maxWait)
        queue.withLock {
            while (true) {
                currentCoroutineContext().ensureActive()
                val now = clock.now()
                val readyAt = maxOf(nextRequestAt, cooldownUntil.get())
                if (now > deadline || readyAt > deadline) {
                    throw NotionRequestDeferredException(requestContext)
                }
                if (readyAt <= now) {
                    nextRequestAt = now.plus(config.minInterval)
                    return@withLock
                }
                sleeper.sleep(Duration.between(now, readyAt))
            }
        }
    }

    fun deferFor(duration: Duration) {
        val until = try {
            clock.now().plus(duration)
        } catch (@Suppress("SwallowedException") exception: DateTimeException) {
            Instant.MAX
        } catch (@Suppress("SwallowedException") exception: ArithmeticException) {
            Instant.MAX
        }
        cooldownUntil.updateAndGet { previous -> maxOf(previous, until) }
    }
}
