package com.sprintstart.sprintstartbackend.shared.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ScheduledExecutor(
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun launch(name: String, job: suspend () -> Unit) {
        applicationScope.launch {
            logger
                .info("Starting scheduled job: $name")
            runCatching { job() }
                .onSuccess { logger.info("Scheduled job $name completed") }
                .onFailure { logger.error("Scheduled job $name failed: $it") }
        }
    }
}
