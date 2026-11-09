package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactBatchResult
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceArtifactFailureStage
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactFailure
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

        val attempts = command.artifacts.map { artifact -> artifact to tryPersist(command, artifact) }
        val results = attempts.mapNotNull { (_, result) -> result }
        val persistenceFailures = attempts
            .filter { (_, result) -> result == null }
            .map { (artifact, _) -> persistenceFailure(artifact) }

        (command.failures + persistenceFailures).forEach { failure ->
            failurePersistenceService.record(command.runId, failure)
        }
        return ConfluenceArtifactBatchResult(
            created = results.count { it == ConfluenceArtifactItemPersistenceResult.CREATED },
            updated = results.count { it == ConfluenceArtifactItemPersistenceResult.UPDATED },
            unchanged = results.count { it == ConfluenceArtifactItemPersistenceResult.UNCHANGED },
            failed = command.failures.size + persistenceFailures.size,
            persistenceFailures = persistenceFailures,
        )
    }

    private fun tryPersist(
        command: ConfluenceArtifactBatchCommand,
        artifact: ConfluencePageArtifactCommand,
    ): ConfluenceArtifactItemPersistenceResult? = try {
        itemPersistenceService.persist(command.runId, command.projectId, artifact)
    } catch (@Suppress("SwallowedException") exception: DataIntegrityViolationException) {
        null
    }

    private fun persistenceFailure(artifact: ConfluencePageArtifactCommand) = ConfluencePageArtifactFailure(
        pageId = artifact.metadata.pageId,
        sourceUrl = artifact.sourceUrl,
        stage = ConfluenceArtifactFailureStage.PERSISTENCE,
        reason = PERSISTENCE_FAILURE_REASON,
    )

    private companion object {
        const val PERSISTENCE_FAILURE_REASON = "Confluence page artifact could not be persisted"
    }
}
