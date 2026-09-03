package com.sprintstart.sprintstartbackend.shared.scheduler

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ScheduledExecutorTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val executor = ScheduledExecutor(scope)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `does not execute more than five scheduled jobs concurrently`() = runTest(dispatcher) {
        val activeJobs = AtomicInteger(0)
        val maximumActiveJobs = AtomicInteger(0)
        val releaseJobs = CompletableDeferred<Unit>()

        val jobs = (1..6).map { index ->
            executor.launch("job-$index") {
                val active = activeJobs.incrementAndGet()
                maximumActiveJobs.updateAndGet { current -> maxOf(current, active) }
                if (index <= 5) {
                    releaseJobs.await()
                }
                activeJobs.decrementAndGet()
            }
        }

        testScheduler.runCurrent()

        assertThat(maximumActiveJobs.get()).isEqualTo(5)
        assertThat(activeJobs.get()).isEqualTo(5)

        releaseJobs.complete(Unit)
        advanceUntilIdle()
        jobs.joinAll()

        assertThat(activeJobs.get()).isZero()
    }
}
