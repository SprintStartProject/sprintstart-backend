package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
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
     * - issues are updated only when the computed issue hash changes; `state`/`labels` are the
     *   exception -- they refresh on every fetch regardless of the hash, since a label or
     *   open/closed change doesn't move title/body
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
        val projectIds = githubRepositoryApi.getRepositoryProjectIdsById(command.metadata.repositoryId).toMutableSet()

        val existing = artifactRepository.findBySourceId(command.sourceId)
        if (existing != null) {
            existing.addProjectIds(projectIds)
            // Backfills rows ingested before the column existed. Not part of the AI payload, so it
            // deliberately does not mark the artifact for re-embedding.
            if (existing.authorLogin == null) {
                existing.authorLogin = command.authorLogin
            }
            when (command.artifactType) {
                ArtifactType.COMMIT -> Unit
                ArtifactType.FILE -> updateFile(existing, command, runId)
                ArtifactType.ISSUE -> updateIssue(existing, command, runId)
                ArtifactType.PULL_REQUEST -> updatePullRequest(existing, command, runId)
            }
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
            state = command.state,
            labels = command.labels.toMutableList(),
            projectIdsInternal = projectIds,
            ingestionRun = ingestionRun,
            hash = command.hash,
            metadata = artifactMetadataJsonMapper.toJson(command.metadata),
            // These were hardcoded to null, so no artifact ever carried the time it was created at
            // the source -- while every mapper was dutifully computing it. Nothing read them, so
            // nothing complained, until onboarding needed to know when a pull request was opened.
            createdAtSource = command.createdAtSource,
            updatedAtSource = command.updatedAtSource,
            aiSyncRunId = runId,
            authorLogin = command.authorLogin,
            mergedAtSource = command.mergedAtSource,
            firstResponseAtSource = command.firstResponseAtSource,
            changesRequestedCount = command.changesRequestedCount,
        )
        artifactRepository.save(artifact)
        ingestionRun.ingestedCount++
    }

    /** Files are content-addressed: nothing to do unless the incoming hash differs. */
    private fun updateFile(artifact: Artifact, command: GithubArtifactCommand, runId: UUID) {
        if (artifact.hash == command.hash) {
            return
        }

        artifact.content = command.bodyText
        artifact.hash = command.hash
        artifact.markAiSyncPending(runId)
        countUpdate(runId)
    }

    /**
     * Issues carry two independently changing parts: the hashed title/body, and `state`/`labels`.
     *
     * State and labels are refreshed regardless of the hash -- an issue being closed or re-labeled
     * doesn't move its title/body, so gating them on hash equality would silently miss exactly the
     * updates they exist for. They are compared first rather than overwritten blindly, because both
     * are part of the AI payload: writing them unconditionally would mark every issue for
     * re-embedding on every crawl.
     */
    private fun updateIssue(artifact: Artifact, command: GithubArtifactCommand, runId: UUID) {
        val metadataChanged = artifact.state != command.state || artifact.labels != command.labels
        artifact.state = command.state
        artifact.labels.clear()
        artifact.labels.addAll(command.labels)

        if (artifact.hash != command.hash) {
            artifact.title = command.title
            artifact.content = command.bodyText
            artifact.hash = command.hash
            artifact.markAiSyncPending(runId)
            countUpdate(runId)
            return
        }

        if (metadataChanged) {
            artifact.markAiSyncPending(runId)
        }
    }

    /**
     * Pull requests are treated as mutable and overwritten on every re-fetch (they carry no hash).
     *
     * The AI index only cares when the embedded text actually moved, though -- re-queuing every
     * pull request on every crawl would keep the whole backlog permanently pending.
     */
    private fun updatePullRequest(artifact: Artifact, command: GithubArtifactCommand, runId: UUID) {
        if (artifact.title != command.title || artifact.content != command.bodyText) {
            artifact.markAiSyncPending(runId)
        }

        artifact.title = command.title
        artifact.content = command.bodyText

        // Lifecycle facts, not content. They change on almost every crawl of an active repository
        // and are not part of what the AI embeds, so they are written unconditionally and
        // deliberately do *not* mark the artifact pending -- otherwise merging one pull request
        // would re-embed it for no reason.
        artifact.state = command.state
        artifact.mergedAtSource = command.mergedAtSource
        artifact.firstResponseAtSource = command.firstResponseAtSource
        artifact.changesRequestedCount = command.changesRequestedCount
        // Backfills rows written before these were persisted; a source creation time never changes.
        if (artifact.createdAtSource == null) {
            artifact.createdAtSource = command.createdAtSource
        }

        countUpdate(runId)
    }

    private fun countUpdate(runId: UUID) {
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
