package com.sprintstart.sprintstartbackend.connectors.github.external

import com.sprintstart.sprintstartbackend.connectors.github.external.dto.PullRequestEvidence
import java.util.UUID

/**
 * Module-facing API for reading GitHub repository connection metadata needed outside the
 * GitHub module.
 */
interface GithubRepositoryApi {
    /**
     * Returns the project ids linked to one stored GitHub repository connection.
     *
     * @param id The internal repository connection identifier.
     * @return All SprintStart project ids currently linked to the repository connection.
     */
    fun getRepositoryProjectIdsById(id: UUID): Set<UUID>

    /**
     * Fetches one pull request's real, observed state on demand -- title, body, state, changed
     * files, CI status, commit messages -- for a repository connection this module already owns.
     *
     * @param repositoryConnectionId The internal repository connection identifier.
     * @param prNumber The pull request number to fetch.
     * @return The pull request's evidence, or null if it does not exist in that repository.
     * @throws NoSuchElementException When no repository connection exists for the given id.
     */
    suspend fun getPullRequestEvidence(repositoryConnectionId: UUID, prNumber: Int): PullRequestEvidence?

    /**
     * Resolves the internal repository connection id for a repository addressed by owner and name.
     *
     * @param owner The repository owner ("owner" part of "owner/name").
     * @param name The repository name ("name" part of "owner/name").
     * @return The repository connection id, or `null` when no connection exists.
     */
    fun getRepositoryIdByOwnerAndName(owner: String, name: String): UUID?

    /**
     * Lists the repository connection ids linked to the given project.
     *
     * @param projectId The project whose connected repositories should be resolved.
     * @return The connected repository ids, empty when the project has no GitHub connections.
     */
    fun getRepositoryIdsByProject(projectId: UUID): List<UUID>

    /**
     * Lists connected GitHub repositories as source instances for status reporting.
     *
     * @param projectId When provided, only repositories connected to that project are returned;
     * otherwise all connected repositories are returned.
     * @return Source-instance views ordered by owner and name for stable rendering.
     */
    fun getSourceInstances(projectId: UUID? = null): List<GithubSourceInstanceDto>

    /**
     * Removes a project from every repository connection linked to it.
     *
     * Called when a project is deleted so no connection keeps referencing a project that no longer
     * exists. The repository connections themselves are kept. Idempotent: a project with no linked
     * connections is a no-op.
     *
     * @param projectId The project to unlink from all repository connections.
     */
    fun removeProjectFromAllRepositories(projectId: UUID)
}
