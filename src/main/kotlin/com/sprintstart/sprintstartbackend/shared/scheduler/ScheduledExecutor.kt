package com.sprintstart.sprintstartbackend.shared.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ScheduledExecutor(
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val slots = Semaphore(5)

    /**
     * Launches a scheduled job as a background task.
     *
     * Supports a global threshold of 5 parallel tasks, if more than 5 tasks come in, they're enqueued.
     *
     * @param name The title of the job to start (used for logs).
     * @param job The actual job to execute.
     */
    fun launch(name: String, job: suspend () -> Unit) = applicationScope.launch {
        slots.withPermit {
            logger
                .info("Starting scheduled job: $name")
            runCatching { job() }
                .onSuccess { logger.info("Scheduled job $name completed") }
                .onFailure { logger.error("Scheduled job $name failed: $it") }
        }
    }
}
