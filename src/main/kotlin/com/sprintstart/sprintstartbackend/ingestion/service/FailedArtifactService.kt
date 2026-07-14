package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class FailedArtifactService(
    private val ingestionRunRepository: IngestionRunRepository,
) {
    /**
     * Appends one failed source artifact to the current run and increments the aggregated failure
     * counter in the same transaction.
     *
     * The individual failed item is preserved for status/history views that need artifact-level
     * error details without scanning connector logs.
     *
     * @param command The failed artifact command containing the run id and source-level failure
     * details.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun addFailedArtifact(command: ArtifactFailedCommand) {
        val run = ingestionRunRepository
            .findByIdOrNull(command.transactionId)
            ?: throw IngestionRunNotFoundException(command.transactionId)
        run.failedItems.add(
            FailedArtifact(
                sourceId = command.sourceId,
                reason = command.reason,
                artifactType = command.artifactType,
                sourceUrl = command.sourceUrl,
            ),
        )
        run.failedCount++
    }
}
