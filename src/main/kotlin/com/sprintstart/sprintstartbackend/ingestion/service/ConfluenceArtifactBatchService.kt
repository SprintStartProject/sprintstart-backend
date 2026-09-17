package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactFailureStage
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageArtifactFailure
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/** Persists a Confluence batch while isolating page-local integrity failures. */
@Service
internal class ConfluenceArtifactBatchService(
    private val itemPersistenceService: ConfluenceArtifactItemPersistenceService,
    private val failurePersistenceService: ConfluenceArtifactFailurePersistenceService,
) {
    fun persist(command: ConfluenceArtifactBatchCommand): ConfluenceArtifactBatchResult {
        val sourceIds = command.artifacts.map { artifact -> artifact.sourceId }
        require(sourceIds.distinct().size == command.artifacts.size) {
            "Confluence ingestion batch contains duplicate source identities"
        }

        var created = 0
        var updated = 0
        var unchanged = 0
        val persistenceFailures = mutableListOf<ConfluencePageArtifactFailure>()

        command.artifacts.forEach { artifact ->
            try {
                when (itemPersistenceService.persist(command.runId, command.projectId, artifact)) {
                    ConfluenceArtifactItemPersistenceResult.CREATED -> created++
                    ConfluenceArtifactItemPersistenceResult.UPDATED -> updated++
                    ConfluenceArtifactItemPersistenceResult.UNCHANGED -> unchanged++
                }
            } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
                persistenceFailures += ConfluencePageArtifactFailure(
                    pageId = artifact.metadata.pageId,
                    sourceUrl = artifact.sourceUrl,
                    stage = ConfluenceArtifactFailureStage.PERSISTENCE,
                    reason = PERSISTENCE_FAILURE_REASON,
                )
            }
        }

        (command.failures + persistenceFailures).forEach { failure ->
            failurePersistenceService.record(command.runId, failure)
        }
        return ConfluenceArtifactBatchResult(
            created = created,
            updated = updated,
            unchanged = unchanged,
            failed = command.failures.size + persistenceFailures.size,
            persistenceFailures = persistenceFailures,
        )
    }

    private companion object {
        const val PERSISTENCE_FAILURE_REASON = "Confluence page artifact could not be persisted"
    }
}
