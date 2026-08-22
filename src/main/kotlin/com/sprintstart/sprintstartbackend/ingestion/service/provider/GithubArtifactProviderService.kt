package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Owns writes to the ingestion artifact store and the mutable parts of `IngestionRun`.
 *
 * Connector listeners do not persist artifacts directly. They first map source-specific events to
 * ingestion commands, then delegate here so version-independent business rules stay in one place:
 * duplicate commits are ignored, files and issues update existing rows only when their effective
 * content changes, pull requests are treated as mutable records, and run counters are updated in
 * the same transaction as the underlying entity changes.
 */
@Service
class GithubArtifactProviderService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val githubRepositoryApi: GithubRepositoryApi,
    private val artifactMetadataJsonMapper: ArtifactMetadataJsonMapper,
) {
    /**
     * Persists or updates an ingestion artifact for the active ingestion run.
     *
     * Business rules:
     * - commits are idempotent by `sourceId`; an already-known commit is ignored
     * - files are updated only when the incoming content hash changes
     * - issues are updated only when the computed issue hash changes
     * - pull requests are always treated as mutable and overwrite title/body on re-fetch
     *
     * Counter side effects happen inside the same transaction:
     * - `ingestedCount` increments only when a new artifact row is created
     * - `updatedCount` increments only when an existing artifact is changed
     *
     * @param command The mapped GitHub artifact command containing source identity, content, and
     * repository metadata.
     */
    @Transactional
    fun persistArtifact(command: GithubArtifactCommand) {
        val runId = command.ingestionRunId
        val projectIds = if (command.metadata is GithubArtifactMetadata) {
            githubRepositoryApi.getRepositoryProjectIdsById(command.metadata.repositoryId).toMutableSet()
        } else {
            mutableSetOf()
        }

        if (handleExistingArtifact(command, projectIds)) {
            return
        }

        val ingestionRun = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
            IngestionRunNotFoundException(runId)
        }
        val artifact = Artifact(
            sourceSystem = command.sourceSystem,
            sourceId = command.sourceId,
            sourceUrl = command.sourceUrl,
            artifactType = command.artifactType,
            title = command.title,
            content = command.bodyText,
            mime = command.mime,
            language = command.language,
            projectIdsInternal = projectIds,
            ingestionRun = ingestionRun,
            hash = command.hash,
            metadata = artifactMetadataJsonMapper.toJson(command.metadata),
            createdAtSource = null,
            updatedAtSource = null,
        )
        artifactRepository.save(artifact)
        ingestionRun.ingestedCount++
    }

    private fun handleExistingArtifact(
        command: GithubArtifactCommand,
        projectIds: Set<UUID>,
    ): Boolean {
        return when (command.artifactType) {
            ArtifactType.COMMIT,
            ArtifactType.ORG_METADATA,
            -> attachProjectsIfExisting(command, projectIds)

            ArtifactType.FILE -> updateExistingFileIfChanged(command, projectIds)
            ArtifactType.ISSUE -> updateExistingIssueIfChanged(command, projectIds)
            ArtifactType.PULL_REQUEST -> updateExistingPullRequest(command, projectIds)
            ArtifactType.PAGE -> error("GitHub artifact commands do not support PAGE artifacts")
        }
    }

    private fun attachProjectsIfExisting(
        command: GithubArtifactCommand,
        projectIds: Set<UUID>,
    ): Boolean {
        return findExistingArtifact(command, projectIds) != null
    }

    private fun updateExistingFileIfChanged(
        command: GithubArtifactCommand,
        projectIds: Set<UUID>,
    ): Boolean {
        val artifact = findExistingArtifact(command, projectIds) ?: return false
        if (artifact.hash != command.hash) {
            artifact.content = command.bodyText
            artifact.hash = command.hash
            incrementUpdatedCount(command.ingestionRunId)
        }
        return true
    }

    private fun updateExistingIssueIfChanged(
        command: GithubArtifactCommand,
        projectIds: Set<UUID>,
    ): Boolean {
        val artifact = findExistingArtifact(command, projectIds) ?: return false
        if (artifact.hash != command.hash) {
            artifact.title = command.title
            artifact.content = command.bodyText
            artifact.hash = command.hash
            incrementUpdatedCount(command.ingestionRunId)
        }
        return true
    }

    private fun updateExistingPullRequest(
        command: GithubArtifactCommand,
        projectIds: Set<UUID>,
    ): Boolean {
        val artifact = findExistingArtifact(command, projectIds) ?: return false
        artifact.title = command.title
        artifact.content = command.bodyText
        incrementUpdatedCount(command.ingestionRunId)
        return true
    }

    private fun findExistingArtifact(
        command: GithubArtifactCommand,
        projectIds: Set<UUID>,
    ): Artifact? {
        val artifact = artifactRepository.findBySourceId(command.sourceId) ?: return null
        artifact.addProjectIds(projectIds)
        return artifact
    }

    private fun incrementUpdatedCount(runId: UUID) {
        val ingestionRun = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
            IngestionRunNotFoundException(runId)
        }
        ingestionRun.updatedCount++
    }

    /**
     * Removes an ingestion file artifact when GitHub reports that the source file was deleted and
     * records its id for AI deindexing at the end of the run.
     *
     * The run is locked because deletion events mutate both `deletedCount` and the deindex list. If
     * no stored artifact exists for the deleted source file, the method leaves the run unchanged.
     *
     * @param event The GitHub file deletion event containing repository identity and file path.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun deleteFileArtifact(event: GithubFileDeletedEvent) {
        val run = ingestionRunRepository.findByIdForUpdate(event.transactionId).orElseThrow {
            IngestionRunNotFoundException(event.transactionId)
        }

        val sourceId = SourceIdFactory.buildSourceId(
            repositoryOwner = event.repositoryOwner,
            repositoryName = event.repositoryName,
            type = ArtifactType.FILE,
            unique = event.path,
        )
        val artifact = artifactRepository.findBySourceId(sourceId) ?: return

        artifactRepository.deleteById(artifact.id)
        run.deletedCount++
        run.artifactIdsToDeindex.add(artifact.id.toString())
    }
}
