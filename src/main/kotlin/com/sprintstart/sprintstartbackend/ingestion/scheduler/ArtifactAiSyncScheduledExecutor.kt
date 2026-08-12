package com.sprintstart.sprintstartbackend.ingestion.scheduler

import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactAiSyncService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives the incremental AI sync: repeatedly drains the artifact outbox while crawls are running.
 *
 * A tick keeps draining until the outbox is empty, so a burst of freshly fetched artifacts is not
 * rationed at one batch per interval; when there is nothing pending it costs a single indexed
 * query. [running] makes ticks non-overlapping -- a slow AI service would otherwise stack up
 * concurrent drains that all fight over the same pending rows.
 */
@Component
class ArtifactAiSyncScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val artifactAiSyncService: ArtifactAiSyncService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    @Scheduled(
        fixedDelayString = "\${sprintstart.ingestion.ai-sync.interval-ms:5000}",
        initialDelayString = "\${sprintstart.ingestion.ai-sync.interval-ms:5000}",
    )
    fun tick() {
        if (!running.compareAndSet(false, true)) {
            logger.debug("Previous AI sync drain still running, skipping this tick")
            return
        }

        scheduledExecutor.launch("Draining the artifact AI sync outbox") {
            try {
                do {
                    val drained = artifactAiSyncService.drainOnce()
                } while (drained > 0)
            } finally {
                running.set(false)
            }
        }
    }
}
