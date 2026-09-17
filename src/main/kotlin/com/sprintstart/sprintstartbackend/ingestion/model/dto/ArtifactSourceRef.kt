package com.sprintstart.sprintstartbackend.ingestion.model.dto

import java.util.UUID

/**
 * Identifies the connected source a stored artifact came from.
 *
 * Artifacts of every connector share one table and are told apart by how their source identity was
 * encoded at ingestion time, which differs per connector. This type keeps that knowledge in one
 * place so project-membership maintenance stays source-agnostic.
 */
sealed interface ArtifactSourceRef {
    /**
     * A connected GitHub repository, whose artifacts carry source ids of the form
     * `github:owner/repo:TYPE:unique`.
     */
    data class GithubRepository(
        val owner: String,
        val name: String,
    ) : ArtifactSourceRef {
        val component: String get() = "$owner/$name"
    }

    /**
     * A connected Jira instance, whose issue artifacts carry source urls of the form
     * `{instanceUrl}/browse/{key}`.
     */
    data class JiraInstance(
        val instanceUrl: String,
    ) : ArtifactSourceRef

    /**
     * A connected Confluence space, whose page artifacts carry source ids of the form
     * `confluence:{connectionId}:page:{pageId}`.
     *
     * Identified by the connection rather than by the space, because that is what the source id
     * holds: the same space connected twice is two connections and two sets of artifacts.
     */
    data class ConfluenceConnection(
        val connectionId: UUID,
    ) : ArtifactSourceRef
}
