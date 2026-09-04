package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageArtifactFailure
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Records one sanitized Confluence page failure in an independent transaction. */
@Service
internal class ConfluenceArtifactFailurePersistenceService(
    private val ingestionRunRepository: IngestionRunRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(runId: UUID, failure: ConfluencePageArtifactFailure) {
        val run = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
            IngestionRunNotFoundException(runId)
        }
        run.failedItems += FailedArtifact(
            sourceId = failure.pageId,
            artifactType = ArtifactType.PAGE,
            sourceUrl = failure.sourceUrl,
            reason = failure.toSafePersistedReason(),
        )
        run.failedCount++
    }

    private fun ConfluencePageArtifactFailure.toSafePersistedReason(): String {
        return buildString {
            append(reason)
            append(" [stage=").append(stage.name)
            append(", httpStatus=").append(httpStatus ?: "n/a")
            append(", attempts=").append(attempts ?: "n/a")
            append(']')
        }
    }
}
