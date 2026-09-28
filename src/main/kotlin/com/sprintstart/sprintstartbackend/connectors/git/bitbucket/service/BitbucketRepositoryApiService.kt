package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.BitbucketRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
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

    @Transactional
    override fun removeProjectFromAllRepositories(projectId: UUID) {
        val connections = connectionRepository.findAllByProjectId(projectId)
        connections.forEach { connection: BitbucketConnection -> connection.projectIdsInternal.remove(projectId) }
        connectionRepository.saveAll(connections)
    }
}
