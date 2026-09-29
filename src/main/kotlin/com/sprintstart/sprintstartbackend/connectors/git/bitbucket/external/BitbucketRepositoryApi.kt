package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external

import java.util.UUID

/**
 * Module-facing API for reading and repairing the project links of connected Bitbucket repositories.
 *
 * Other modules — ingestion above all — must not reach into the connector's persistence. The project
 * links matter to them because ingestion only announces an artifact to the AI index for the projects
 * its repository is linked to, so a caller that cannot read these ids cannot decide where an ingested
 * artifact belongs.
 *
 * Mirrors the GitHub connector's `GithubRepositoryApi`.
 */
interface BitbucketRepositoryApi {
    /**
     * Returns the project ids linked to one stored Bitbucket repository connection.
     *
     * @param id The internal repository connection identifier.
     * @return All SprintStart project ids currently linked to the repository connection.
     * @throws NoSuchElementException When no repository connection exists for the given id.
     */
    fun getRepositoryProjectIdsById(id: UUID): Set<UUID>

    /**
     * Resolves the internal repository connection id for a repository addressed by its coordinates.
     *
     * Used to turn a connector event that only carries `workspace`/`slug` back into the connection
     * id the ingestion run and its artifacts are keyed by.
     *
     * @param workspace The Bitbucket workspace owning the repository.
     * @param slug The repository's slug within [workspace].
     * @return The repository connection id, or `null` when no connection exists.
     */
    fun getRepositoryIdByWorkspaceAndSlug(workspace: String, slug: String): UUID?

    /**
     * Lists the repository connection ids linked to the given project.
     *
     * @param projectId The project whose connected repositories should be resolved.
     * @return The connected repository ids, empty when the project has no Bitbucket connections.
     */
    fun getRepositoryIdsByProject(projectId: UUID): List<UUID>

    /**
     * Lists connected Bitbucket repositories as source instances for status reporting.
     *
     * @param projectId When provided, only repositories connected to that project are returned;
     * otherwise all connected repositories are returned.
     * @return Source-instance views ordered by workspace and slug for stable rendering.
     */
    fun getSourceInstances(projectId: UUID? = null): List<BitbucketSourceInstanceDto>

    /**
     * Removes a project from every repository connection linked to it.
     *
     * Called when a project is deleted so no connection keeps referencing a project that no longer
     * exists. The repository connections themselves are kept, so a project deleted by mistake does
     * not silently drop the repository's collected artifacts. Idempotent: a project with no linked
     * connections is a no-op.
     *
     * @param projectId The project to unlink from all repository connections.
     */
    fun removeProjectFromAllRepositories(projectId: UUID)
}
