package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant.now
import java.time.temporal.ChronoUnit
import kotlin.collections.forEach

@Component
class IngestionRunMonitor(private val ingestionRunRepository: IngestionRunRepository) {

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    fun monitorRunStatus() {
        ingestionRunRepository.findAllByStatus(IngestionRunStatus.RUNNING)
            .forEach { run ->
                if (Duration.between(run.startedAt, now())
                    >= Duration.of(10, ChronoUnit.MINUTES)
                ) {
                    run.finishedAt = now()
                    run.failedCount = run.expectedArtifacts.size - run.processedArtifacts.size
                    run.expectedArtifacts.forEach {

                    }

                    if (run.ingestedCount + run.updatedCount >= 1) {

                        run.status = IngestionRunStatus.PARTIAL

                    } else run.status = IngestionRunStatus.FAILED

                }
            }
    }
}