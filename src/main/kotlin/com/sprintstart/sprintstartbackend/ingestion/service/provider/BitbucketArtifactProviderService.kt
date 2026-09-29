package com.sprintstart.sprintstartbackend.ingestion.service.provider

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesResyncedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketWorkspaceMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.BitbucketArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildBitbucketSourceId
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.escapeLikeLiteral
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Owns writes to the ingestion artifact store and the mutable parts of `IngestionRun` for Bitbucket.
 *
 * The Bitbucket counterpart of [GithubArtifactProviderService], sharing its business rules:
 * duplicate commits are ignored, files update in place only when their hash changed, and pull
 * requests are treated as mutable records. It differs in the project lookup — it asks the Bitbucket
 * module API — and in supporting only the artifact types Bitbucket produces: there is no issue
 * shape here, because Bitbucket removed its native issue tracker. The workspace metadata artifact
 * belongs to no single repository, so it resolves to the union of its workspace's project links.
 *
 * Connector listeners do not persist artifacts directly. They map source-specific events to
 * commands first, then delegate here so version-independent rules stay in one place, and run
 * counters are updated in the same transaction as the underlying entity change.
 *
 * @constructor Creates the service from its repositories and the Bitbucket module API.
 * @param ingestionRunRepository Loads and locks the run the counters are updated on.
 * @param artifactRepository Reads and writes artifacts, keyed by source id.
 * @param bitbucketRepositoryApi Resolves the projects a connected repository is linked to.
 * @param artifactMetadataJsonMapper Serializes artifact metadata for the store.
 */
@Service
class BitbucketArtifactProviderService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val bitbucketRepositoryApi: BitbucketRepositoryApi,
    private val artifactMetadataJsonMapper: ArtifactMetadataJsonMapper,
) {
    /**
     * Persists or updates an ingestion artifact for the active ingestion run.
     *
     * Business rules:
     * - commits are idempotent by `sourceId`; an already-known commit is ignored
     * - files are updated only when the incoming content hash changes
     * - pull requests are always treated as mutable and overwrite title/body on re-fetch
     *
     * Counter side effects happen inside the same transaction:
     * - `ingestedCount` increments only when a new artifact row is created
     * - `updatedCount` increments only when an existing artifact is changed
     *
     * @param command The mapped Bitbucket artifact command.
     */
    @Transactional
    fun persistArtifact(command: BitbucketArtifactCommand) {
        val runId = command.ingestionRunId
        val projectIds = resolveProjectIds(command.metadata)

        val existing = artifactRepository.findBySourceId(command.sourceId)
        if (existing != null) {
            updateExisting(existing, command, projectIds, runId)
            return
        }

        storeNew(command, projectIds, runId)
    }

    /**
     * Resolves the projects an artifact belongs to.
     *
     * Repository artifacts are scoped by their connection's project links, so their metadata names
     * the repository to ask the Bitbucket module API about. The workspace metadata artifact is
     * workspace-level and belongs to no single repository, so it resolves to the union of the
     * projects of every connected repository in the workspace.
     *
     * @param metadata The metadata the command carries.
     * @return The ids of the projects the artifact belongs to.
     */
    private fun resolveProjectIds(metadata: ArtifactMetadata): MutableSet<UUID> =
        when (metadata) {
            is BitbucketArtifactMetadata -> {
                bitbucketRepositoryApi
                    .getRepositoryProjectIdsById(metadata.repositoryId)
                    .toMutableSet()
            }
            is BitbucketWorkspaceMetadataArtifactMetadata -> {
                bitbucketRepositoryApi
                    .getWorkspaceProjectIds(metadata.workspace)
                    .toMutableSet()
            }
            else -> mutableSetOf()
        }

    /**
     * Applies a re-fetch to an artifact an earlier run already stored.
     *
     * Everything the AI payload carries has to reach the index, so a newly linked project and a
     * changed pull-request state mark the artifact for re-ingestion. Only a content change counts
     * as an update of the run: linking a repository to a second project, or merging a pull request,
     * does not change what was fetched.
     *
     * @param artifact The stored artifact matching the command's source id.
     * @param command The mapped Bitbucket artifact command.
     * @param projectIds The projects the artifact's repository is currently linked to.
     * @param runId The active ingestion run.
     */
    private fun updateExisting(
        artifact: Artifact,
        command: BitbucketArtifactCommand,
        projectIds: Set<UUID>,
        runId: UUID,
    ) {
        val linked = artifact.addProjectIds(projectIds)
        val change = applyChange(artifact, command)

        if (!linked && !change.reachesTheIndex) return

        val ingestionRun = lockRun(runId)
        if (change.content) {
            artifact.lastChangedAt = Instant.now()
            ingestionRun.updatedCount++
        }
        ingestionRun.artifactIdsToReingest.add(artifact.id)
    }

    /**
     * Overwrites the stored fields the source changed, per artifact type.
     *
     * @param artifact The stored artifact to update in place.
     * @param command The mapped Bitbucket artifact command carrying the freshly fetched content.
     * @return What changed, see [ArtifactChange].
     */
    private fun applyChange(artifact: Artifact, command: BitbucketArtifactCommand): BitbucketArtifactChange =
        when (command.artifactType) {
            // Immutable once fetched: a re-fetch yields the same content, so only a new project
            // link is ever worth acting on.
            ArtifactType.COMMIT -> BitbucketArtifactChange.NOTHING

            ArtifactType.FILE -> {
                if (artifact.hash == command.hash) {
                    BitbucketArtifactChange.NOTHING
                } else {
                    artifact.content = command.bodyText
                    artifact.hash = command.hash
                    artifact.sourceUrl = command.sourceUrl
                    BitbucketArtifactChange(content = true)
                }
            }

            // Pull requests carry no content hash, so the stored title and body are compared
            // directly. Overwriting them unconditionally would count every re-fetch as an update:
            // it would inflate the run's update count and re-send unchanged pull requests to be
            // embedded again.
            ArtifactType.PULL_REQUEST -> {
                val trackingChanged = artifact.state != command.state
                // Refreshed on every fetch, for the same reason as GitHub's: a pull request being
                // merged or reviewed moves none of its text.
                artifact.state = command.state
                artifact.mergedAtSource = command.mergedAtSource
                artifact.firstResponseAtSource = command.firstResponseAtSource
                artifact.changesRequestedCount = command.changesRequestedCount
                // Backfills rows written before these were persisted; a source creation time never changes.
                if (artifact.createdAtSource == null) {
                    artifact.createdAtSource = command.createdAtSource
                }

                val contentChanged =
                    artifact.title != command.title || artifact.content != command.bodyText
                if (contentChanged) {
                    artifact.title = command.title
                    artifact.content = command.bodyText
                    artifact.sourceUrl = command.sourceUrl
                }
                BitbucketArtifactChange(content = contentChanged, tracking = trackingChanged)
            }

            // Workspace metadata rots: members join and leave, display data changes. A re-fetch
            // overwrites the stored payload so the artifact never fossilizes, and a changed payload
            // re-indexes like any other content change — while an identical one stays quiet.
            ArtifactType.ORG_METADATA -> {
                val metadata = artifactMetadataJsonMapper.toJson(command.metadata)
                val contentChanged = artifact.metadata != metadata || artifact.title != command.title
                if (contentChanged) {
                    artifact.metadata = metadata
                    artifact.title = command.title
                }
                BitbucketArtifactChange(content = contentChanged)
            }

            // The remaining types never travel through this provider: issues come from Jira and
            // pages have Confluence's own provider.
            ArtifactType.ISSUE,
            ArtifactType.PAGE,
            -> error("Bitbucket artifact commands do not support ${command.artifactType} artifacts")
        }

    /**
     * Stores an artifact this run is the first to see.
     *
     * @param command The mapped Bitbucket artifact command.
     * @param projectIds The projects the artifact's repository is currently linked to.
     * @param runId The active ingestion run.
     */
    private fun storeNew(
        command: BitbucketArtifactCommand,
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
            labels = mutableListOf(),
            projectIdsInternal = projectIds,
            ingestionRun = ingestionRun,
            hash = command.hash,
            metadata = artifactMetadataJsonMapper.toJson(command.metadata),
            createdAtSource = command.createdAtSource,
            updatedAtSource = command.updatedAtSource,
            authorLogin = null,
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
     * Removes an ingestion file artifact when Bitbucket reports that the source file was deleted and
     * records its id for AI deindexing at the end of the run.
     *
     * The run is locked because deletion events mutate both `deletedCount` and the deindex list. If
     * no stored artifact exists for the deleted source file, the method leaves the run unchanged.
     *
     * @param event The Bitbucket file deletion event containing repository identity and file path.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun deleteFileArtifact(event: BitbucketFileDeletedEvent) {
        val run = ingestionRunRepository.findByIdForUpdate(event.transactionId).orElseThrow {
            IngestionRunNotFoundException(event.transactionId)
        }

        val sourceId = buildBitbucketSourceId(
            workspace = event.workspace,
            slug = event.slug,
            type = ArtifactType.FILE,
            unique = event.path,
        )
        val artifact = artifactRepository.findBySourceId(sourceId) ?: return

        artifactRepository.deleteById(artifact.id)
        run.deletedCount++
        run.artifactIdsToDeindex.add(artifact.id.toString())
    }

    /**
     * Removes stored file artifacts a fallback full ingest did not see, and records them for AI
     * deindexing at the end of the run.
     *
     * A full ingest re-upserts everything it visits but never reports deletions, so files removed
     * while the cursor revision was missing would otherwise linger in the store and the index
     * forever. Only file artifacts of this repository are candidates, and only those whose path is
     * absent from the visited set go: failed reads are visited too, so an unreadable file is kept,
     * not mistaken for a deleted one.
     *
     * The run is locked once for the whole reconciliation, like the single-delete path locks it per
     * file. When nothing is stale the run is left untouched.
     *
     * @param event The resync event carrying repository identity and every path the full ingest saw.
     * @throws IngestionRunNotFoundException when the run id is unknown.
     */
    @Transactional
    fun reconcileDeletedFiles(event: BitbucketFilesResyncedEvent) {
        val prefix = "bitbucket:${event.workspace}/${event.slug}:${ArtifactType.FILE}:"
        val stale = artifactRepository
            .findAllBySourceIdPrefix(escapeLikeLiteral(prefix))
            .filter { it.artifactType == ArtifactType.FILE }
            .filter { it.sourceId.startsWith(prefix) }
            .filter { it.sourceId.removePrefix(prefix) !in event.visitedPaths }
        if (stale.isEmpty()) return

        val run = lockRun(event.transactionId)
        stale.forEach { artifact ->
            artifactRepository.deleteById(artifact.id)
            run.deletedCount++
            run.artifactIdsToDeindex.add(artifact.id.toString())
        }
    }
}

/**
 * What a re-fetch changed about a stored artifact.
 *
 * The same two axes the GitHub provider tracks: [content] is what the run reports as an update and
 * what moves `lastChangedAt`; [tracking] is issue-tracker-style state — a pull request's merged or
 * closed status — which is not a content change but which the AI payload carries.
 *
 * @property content Whether the artifact's text changed.
 * @property tracking Whether its pull-request state changed.
 */
private data class BitbucketArtifactChange(
    val content: Boolean = false,
    val tracking: Boolean = false,
) {
    /** Whether anything changed that the AI service has to be told about. */
    val reachesTheIndex: Boolean get() = content || tracking

    companion object {
        val NOTHING = BitbucketArtifactChange()
    }
}
