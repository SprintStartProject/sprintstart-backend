package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketSourceInstanceDto
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.bitbucketRepositoryUrl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Repository-backed implementation of the Bitbucket module API exposed to other modules.
 */
@Service
internal class BitbucketRepositoryApiService(
    private val connectionRepository: BitbucketConnectionRepository,
) : BitbucketRepositoryApi {
    /**
     * Resolves the project ids currently associated with one Bitbucket repository connection.
     *
     * @param id The internal repository connection identifier.
     * @return The set of linked SprintStart project ids.
     * @throws NoSuchElementException When no repository connection exists for the given id.
     */
    override fun getRepositoryProjectIdsById(id: UUID): Set<UUID> {
        val connection = connectionRepository.findById(id).orElseThrow {
            NoSuchElementException("Bitbucket repository with id $id not found")
        }
        return connection.projectIds
    }

    override fun getRepositoryIdByWorkspaceAndSlug(workspace: String, slug: String): UUID? =
        connectionRepository.findByWorkspaceAndSlug(workspace, slug)?.id

    override fun getRepositoryIdsByProject(projectId: UUID): List<UUID> =
        connectionRepository.findAllByProjectId(projectId).map { it.id }

    @Transactional(readOnly = true)
    override fun getSourceInstances(projectId: UUID?): List<BitbucketSourceInstanceDto> {
        val connections = if (projectId != null) {
            connectionRepository.findAllByProjectId(projectId)
        } else {
            connectionRepository.findAll()
        }

        return connections
            .sortedWith(compareBy({ it.workspace }, { it.slug }))
            .map { it.toSourceInstanceDto() }
    }

    @Transactional
    override fun removeProjectFromAllRepositories(projectId: UUID) {
        val connections = connectionRepository.findAllByProjectId(projectId)
        connections.forEach { connection: BitbucketConnection -> connection.projectIdsInternal.remove(projectId) }
        connectionRepository.saveAll(connections)
    }

    private fun BitbucketConnection.toSourceInstanceDto(): BitbucketSourceInstanceDto =
        BitbucketSourceInstanceDto(
            repositoryId = id,
            workspace = workspace,
            slug = slug,
            sourceUrl = bitbucketRepositoryUrl(workspace, slug),
            status = toSourceStatus(),
            enabled = sourceEnabled,
            lastPullRequestsSyncAt = lastPullRequestsSyncAt,
        )
}

/**
 * Maps a Bitbucket repository connection to the stable source-status vocabulary shared by the
 * connector overview and the ingestion status APIs.
 *
 * A disabled source always reports `DISABLED`, regardless of its underlying connection state, so a
 * paused repository is not shown as actively connected. Mirrors the GitHub connector's
 * `toSourceStatus` and the Jira connector's, keeping the mapping owned by the Bitbucket module.
 *
 * Read by both [BitbucketRepositoryApiService] and [BitbucketProjectSourceProvider], so the two
 * views of a connection cannot report different statuses.
 */
internal fun BitbucketConnection.toSourceStatus(): String {
    if (!sourceEnabled) {
        return "DISABLED"
    }

    return when (connectionState) {
        ConnectionState.UP_TO_DATE -> "CONNECTED"
        ConnectionState.UPDATING -> "UPDATING"
        ConnectionState.OUT_OF_DATE -> "OUT_OF_DATE"
        ConnectionState.FAILED -> "FAILED"
    }
}
