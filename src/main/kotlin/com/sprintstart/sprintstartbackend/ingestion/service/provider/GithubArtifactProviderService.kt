package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.GithubArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant
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
        val projectIds = if (command.metadata is GithubArtifactMetadata) {
            githubRepositoryApi.getRepositoryProjectIdsById(command.metadata.repositoryId).toMutableSet()
        } else {
            mutableSetOf()
        }

        val existing = artifactRepository.findBySourceId(command.sourceId)
        if (existing != null) {
            updateExisting(existing, command, projectIds, runId)
            return
        }

        storeNew(command, projectIds, runId)
    }

    /**
     * Applies a re-fetch to an artifact an earlier run already stored.
     *
     * Both a content change and a newly linked project have to reach the AI index, so either marks
     * the artifact for re-ingestion. Only a content change counts as an update of the run: linking a
     * repository to a second project does not change what was fetched.
     *
     * @param artifact The stored artifact matching the command's source id.
     * @param command The mapped GitHub artifact command.
     * @param projectIds The projects the artifact's repository is currently linked to.
     * @param runId The active ingestion run.
     */
    private fun updateExisting(
        artifact: Artifact,
        command: GithubArtifactCommand,
        projectIds: Set<UUID>,
        runId: UUID,
    ) {
        val linked = artifact.addProjectIds(projectIds)
        // Backfills rows ingested before the column existed. Not part of the AI payload, so it
        // deliberately does not mark the artifact for re-embedding.
        if (artifact.authorLogin == null) {
            artifact.authorLogin = command.authorLogin
        }
        val contentChanged = applyContentChange(artifact, command)

        if (!linked && !contentChanged) return

        val ingestionRun = lockRun(runId)
        if (contentChanged) {
            artifact.lastChangedAt = Instant.now()
            ingestionRun.updatedCount++
        }
        ingestionRun.artifactIdsToReingest.add(artifact.id)
    }

    /**
     * Overwrites the stored content when the source changed, per artifact type.
     *
     * @param artifact The stored artifact to update in place.
     * @param command The mapped GitHub artifact command carrying the freshly fetched content.
     * @return `true` when the artifact's effective content changed.
     */
    private fun applyContentChange(artifact: Artifact, command: GithubArtifactCommand): Boolean =
        when (command.artifactType) {
            // Immutable once fetched: a re-fetch yields the same content, so only a new project
            // link is ever worth acting on.
            ArtifactType.COMMIT,
            ArtifactType.ORG_METADATA,
            -> false

            ArtifactType.FILE -> {
                if (artifact.hash == command.hash) {
                    false
                } else {
                    artifact.content = command.bodyText
                    artifact.hash = command.hash
                    true
                }
            }

            // State and labels are refreshed on every fetch, regardless of the hash: an issue being
            // closed or re-labeled doesn't move its title or body, so gating them on hash equality
            // would silently miss exactly the updates they exist for. Neither counts as a content
            // change -- they leave `lastChangedAt` and the run's update count alone.
            ArtifactType.ISSUE -> {
                artifact.state = command.state
                artifact.labels.clear()
                artifact.labels.addAll(command.labels)

                if (artifact.hash == command.hash) {
                    false
                } else {
                    artifact.title = command.title
                    artifact.content = command.bodyText
                    artifact.hash = command.hash
                    true
                }
            }

            // Pull requests carry no content hash, so the stored title and body are compared
            // directly. Overwriting them unconditionally, as this did before, counted every
            // re-fetch as an update: it inflated the run's update count, re-sent unchanged pull
            // requests to be embedded again, and would make `lastChangedAt` move on a sync that
            // changed nothing.
            ArtifactType.PULL_REQUEST -> {
                // Refreshed on every fetch, for the same reason as an issue's state: a pull request
                // being merged or reviewed moves none of its text.
                artifact.state = command.state
                artifact.mergedAtSource = command.mergedAtSource
                artifact.firstResponseAtSource = command.firstResponseAtSource
                artifact.changesRequestedCount = command.changesRequestedCount
                // Backfills rows written before these were persisted; a source creation time never changes.
                if (artifact.createdAtSource == null) {
                    artifact.createdAtSource = command.createdAtSource
                }

                if (artifact.title == command.title && artifact.content == command.bodyText) {
                    false
                } else {
                    artifact.title = command.title
                    artifact.content = command.bodyText
                    true
                }
            }
            return
        }

    /**
     * Stores an artifact this run is the first to see.
     *
     * @param command The mapped GitHub artifact command.
     * @param projectIds The projects the artifact's repository is currently linked to.
     * @param runId The active ingestion run.
     */
    private fun storeNew(
        command: GithubArtifactCommand,
        projectIds: MutableSet<UUID>,
        runId: UUID,
    ) {
        val ingestionRun = lockRun(runId)
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
            createdAtSource = command.createdAtSource,
            updatedAtSource = command.updatedAtSource,
            authorLogin = command.authorLogin,
            mergedAtSource = command.mergedAtSource,
            firstResponseAtSource = command.firstResponseAtSource,
            changesRequestedCount = command.changesRequestedCount,
        )
        artifactRepository.save(artifact)
        ingestionRun.ingestedCount++
    }

    /**
     * Loads the active run with a write lock, the way every counter and collection mutation here
     * needs it.
     *
     * @param runId The ingestion run to lock.
     * @return The locked run.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    private fun lockRun(runId: UUID): IngestionRun =
        ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
            IngestionRunNotFoundException(runId)
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
