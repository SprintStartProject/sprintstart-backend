package com.sprintstart.sprintstartbackend.ingestion.model.dto

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
}
