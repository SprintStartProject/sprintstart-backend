package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AssignedIssue
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredArtifact
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.ingestion.external.IngestedIssue
import com.sprintstart.sprintstartbackend.ingestion.external.RepositoryResponsiveness
import com.sprintstart.sprintstartbackend.ingestion.external.TaskSourceArtifact
import com.sprintstart.sprintstartbackend.ingestion.external.model.ArtifactDto
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.external.model.toDto
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Service implementation of the ingestion metadata API used by other modules.
 *
 * A small read-only adapter over the artifact repository; it does not touch the ingestion write
 * path or expose internal ingestion entities.
 */
@Service
@Suppress("TooManyFunctions")
internal class ArtifactIngestionApiService(
    private val artifactRepository: ArtifactRepository,
    private val artifactMetadataJsonMapper: ArtifactMetadataJsonMapper,
    private val assignedIssueReader: AssignedIssueReader,
) : ArtifactIngestionApi {
    @Transactional(readOnly = true)
    @Tracked("Retrieving tracked issues assigned to a person")
    override fun getAssignedIssues(projectId: UUID, assigneeDisplayName: String): List<AssignedIssue> {
        // A blank name matches nobody rather than everybody. Without this an empty display name
        // would fall through to the reader, where `null == ""` is false but a Jira account with a
        // blank name is not -- and crediting somebody with every unassigned issue in a project is
        // the loudest possible version of the wrong answer.
        if (assigneeDisplayName.isBlank()) {
            return emptyList()
        }
        return artifactRepository
            .findAllByProjectIdAndSourceSystemAndArtifactType(projectId, SourceSystem.JIRA, ArtifactType.ISSUE)
            .mapNotNull { assignedIssueReader.read(it, assigneeDisplayName) }
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving first ingestion time of component")
    override fun getFirstIngestedAt(component: String): Instant? {
        return artifactRepository.findFirstIngestedAt(component)
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving first ingestion time of list of components")
    override fun getFirstIngestedAt(components: Collection<String>): Map<String, Instant> {
        return components
            .distinct()
            .mapNotNull { component ->
                artifactRepository.findFirstIngestedAt(component)?.let { component to it }
            }.toMap()
    }

    @Transactional(readOnly = true)
    @Tracked("Checking if artifact exists")
    override fun exists(artifactId: UUID): Boolean {
        return artifactRepository.existsById(artifactId)
    }

    @Transactional(readOnly = true)
    @Tracked("Checking if artifact exists in project")
    override fun existsInProject(projectId: UUID, artifactId: UUID): Boolean {
        return artifactRepository.findById(artifactId).map { it.projectIds.contains(projectId) }.orElse(false)
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving hash of artifact")
    override fun getHash(artifactId: UUID): String? {
        return artifactRepository.findById(artifactId).orElse(null)?.hash
    }

    @Transactional(readOnly = true)
    override fun getAuthoredPullRequests(projectId: UUID, authorLogin: String): List<AuthoredPullRequest> {
        return artifactRepository
            .findAllByProjectIdAndAuthorLogin(projectId, authorLogin.lowercase())
            .filter { it.artifactType == ArtifactType.PULL_REQUEST }
            .map {
                AuthoredPullRequest(
                    artifactId = it.id,
                    openedAt = it.createdAtSource,
                    firstResponseAt = it.firstResponseAtSource,
                    mergedAt = it.mergedAtSource,
                    state = it.state,
                    changesRequestedCount = it.changesRequestedCount,
                    repositoryFullName = (
                        artifactMetadataJsonMapper.fromJson(it.metadata) as? GithubArtifactMetadata
                    )?.repositoryFullName,
                    // Source ids are `github:owner/name:PULL_REQUEST:<number>`, so the number is
                    // already in the identifier and needs no extra lookup.
                    number = it.sourceId.substringAfterLast(':').toIntOrNull(),
                    title = it.title,
                    sourceUrl = it.sourceUrl,
                )
            }
    }

    @Transactional(readOnly = true)
    override fun getAuthoredWork(projectId: UUID, authorLogin: String): List<AuthoredArtifact> {
        return artifactRepository
            .findAllByProjectIdAndAuthorLogin(projectId, authorLogin.lowercase())
            .map { artifact ->
                val metadata = artifactMetadataJsonMapper.fromJson(artifact.metadata)
                AuthoredArtifact(
                    artifactType = artifact.artifactType.name,
                    repositoryFullName = (metadata as? GithubArtifactMetadata)?.repositoryFullName,
                    labels = artifact.labels.toList(),
                )
            }
    }

    @Transactional(readOnly = true)
    override fun getRepositoryResponsiveness(projectId: UUID): List<RepositoryResponsiveness> {
        return artifactRepository
            .findAllByProjectIdAndArtifactType(projectId, ArtifactType.PULL_REQUEST)
            .groupBy {
                (artifactMetadataJsonMapper.fromJson(it.metadata) as? GithubArtifactMetadata)?.repositoryFullName
            }.mapNotNull { (repository, pullRequests) ->
                if (repository == null) return@mapNotNull null
                // A pull request that was merged without a recorded response still got attention,
                // so only one with neither is counted as unanswered.
                val latencies = pullRequests.mapNotNull { hoursBetween(it.createdAtSource, it.firstResponseAtSource) }
                val unanswered = pullRequests.count {
                    it.firstResponseAtSource == null && it.mergedAtSource == null && it.createdAtSource != null
                }
                RepositoryResponsiveness(
                    repositoryFullName = repository,
                    medianHoursToFirstResponse = median(latencies),
                    answeredCount = latencies.size,
                    unansweredCount = unanswered,
                )
            }
    }

    @Transactional(readOnly = true)
    @Tracked("Listing a project's open tracker issues")
    override fun getOpenIssues(projectId: UUID): List<IngestedIssue> {
        return artifactRepository
            .findAllByProjectIdAndArtifactType(projectId, ArtifactType.ISSUE)
            // Only a definite "OPEN" qualifies. A row whose state was never captured is unknown,
            // and offering an issue that may already be finished costs a hire more than not
            // offering one that is fine -- the same direction mining chooses.
            .filter { OPEN_STATE.equals(it.state, ignoreCase = true) }
            .map { it.toIngestedIssue() }
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving one ingested issue by source id")
    override fun getIssue(sourceId: String): IngestedIssue? {
        val artifact = artifactRepository.findBySourceId(sourceId) ?: return null
        if (artifact.artifactType != ArtifactType.ISSUE) return null
        return artifact.toIngestedIssue()
    }

    private fun Artifact.toIngestedIssue(): IngestedIssue =
        IngestedIssue(
            sourceId = sourceId,
            tracker = sourceSystem.name,
            title = title,
            body = content,
            labels = labels.toList(),
            sourceUrl = sourceUrl,
            state = state,
            hasAssignee = hasAssignee,
            updatedAtSource = updatedAtSource,
        )

    @Transactional(readOnly = true)
    override fun getTaskSource(sourceId: String): TaskSourceArtifact? {
        val artifact = artifactRepository.findBySourceId(sourceId) ?: return null
        return TaskSourceArtifact(
            title = artifact.title,
            body = artifact.content,
            labels = artifact.labels.toList(),
            sourceUrl = artifact.sourceUrl,
        )
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving artifact by id")
    override fun findArtifactById(artifactId: UUID): ArtifactDto? {
        return artifactRepository.findById(artifactId).map { it.toDto() }.orElse(null)
    }
}

/** How the mappers spell "still open at the source", for both GitHub and Jira. */
private const val OPEN_STATE = "OPEN"

private fun hoursBetween(from: Instant?, to: Instant?): Long? {
    if (from == null || to == null) return null
    return Duration.between(from, to).toHours()
}

/** Lower median, so an even-sized sample reports a latency that actually occurred. */
private fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    return sorted[(sorted.size - 1) / 2]
}
