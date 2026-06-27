package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class GithubFetchingCompletionTracker(
    private val artifactIngestionService: ArtifactIngestionService,
    private val ingestionRunRepository: IngestionRunRepository,
) {

    fun markFetchPhaseFinished(runId: UUID, finishedType: FinishedTypes) {
        val run = ingestionRunRepository
            .findById(runId)
            .orElseThrow{ NoSuchElementException("Run with id $runId not found") }
        run.finishedTypes.add(finishedType)
        if(run.finishedTypes.containsAll(FinishedTypes.entries)){
            if(run.failedCount > 0) {
                if(run.ingestedCount > 0 || run.updatedCount > 0){
                    run.status = IngestionRunStatus.PARTIAL
                }else run.status = IngestionRunStatus.FAILED
            }else run.status = IngestionRunStatus.COMPLETED
        }
        run.finishedAt = Instant.now()
    }
}