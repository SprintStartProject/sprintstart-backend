package com.sprintstart.sprintstartbackend.connectors.jira.external

import java.util.UUID

/**
 * Module-facing API for reading Jira instance connection metadata needed outside the Jira module.
 *
 * Mirrors [com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi] so the
 * ingestion module can build per-source-instance status rows and resolve project filters for Jira
 * exactly the way it already does for GitHub, without depending on the Jira persistence model.
 *
 * Jira instances are identified by their URL rather than a numeric id, so the connector-neutral
 * source reference used by the ingestion module (`IngestionRun.sourceInstanceRef`) is the instance
 * URL.
 */
interface JiraInstanceApi {
    /**
     * Lists connected Jira instances as source instances for status reporting.
     *
     * @param projectId When provided, only instances connected to that project are returned;
     * otherwise all connected instances are returned.
     * @return Source-instance views ordered by display name and URL for stable rendering.
     */
    fun getSourceInstances(projectId: UUID? = null): List<JiraSourceInstanceDto>

    /**
     * Lists the connector-neutral source references (instance URLs) linked to the given project.
     *
     * Used by the ingestion run history to resolve a project filter to the Jira instances connected
     * to it, mirroring the GitHub connector's `getRepositoryIdsByProject`.
     *
     * @param projectId The project whose connected Jira instances should be resolved.
     * @return The connected instance URLs, empty when the project has no Jira connections.
     */
    fun getInstanceRefsByProject(projectId: UUID): List<String>

    /**
     * Removes a project from every Jira instance linked to it.
     *
     * Called when a project is deleted so no instance keeps referencing a project that no longer
     * exists. The instances themselves are kept. Idempotent: a project with no linked instances is a
     * no-op. Mirrors the GitHub connector's `removeProjectFromAllRepositories`.
     *
     * @param projectId The project to unlink from all Jira instances.
     */
    fun removeProjectFromAllInstances(projectId: UUID)
}
