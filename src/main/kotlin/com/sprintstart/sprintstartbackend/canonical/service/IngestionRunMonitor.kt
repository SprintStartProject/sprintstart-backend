package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.Instant.now
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import kotlin.collections.forEach

@Component
class IngestionRunMonitor(private val ingestionRunRepository: IngestionRunRepository) {

    @Scheduled
    fun monitorRunStatus(){
        ingestionRunRepository.findAllByStatus(IngestionRunStatus.RUNNING)
            .forEach { run ->
                if (Duration.between(now(), run.startedAt)
                    >= Duration.of(10, ChronoUnit.MINUTES)) {
                    run.finishedAt = Instant.now()
                    run.failedCount = run.expectedArtifacts.size - run.processedArtifacts.size
                    run.status = IngestionRunStatus.COMPLETED
                } else {

                }
            }
    }
}