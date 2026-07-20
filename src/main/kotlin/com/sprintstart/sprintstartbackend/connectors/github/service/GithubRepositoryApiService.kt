package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Repository-backed implementation of the GitHub module API exposed to other modules.
 */
@Service
class GithubRepositoryApiService(
    private val githubRepositoryConnectionRepository: GithubRepositoryConnectionRepository,
) : GithubRepositoryApi {
    /**
     * Resolves the project ids currently associated with one GitHub repository connection.
     *
     * @param id The internal repository connection identifier.
     * @return The set of linked SprintStart project ids.
     * @throws NoSuchElementException When no repository connection exists for the given id.
     */
    override fun getRepositoryProjectIdsById(id: UUID): Set<UUID> {
        val repo = githubRepositoryConnectionRepository.findById(id).orElseThrow {
            NoSuchElementException("Repository with id $id not found")
        }
        return repo.projectIds
    }
}
