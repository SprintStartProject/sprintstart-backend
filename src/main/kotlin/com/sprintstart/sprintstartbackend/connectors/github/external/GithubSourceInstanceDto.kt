package com.sprintstart.sprintstartbackend.connectors.github.external

import java.time.Instant
import java.util.UUID

/**
 * Module-facing view of a connected GitHub repository, exposing only the fields other modules need
 * to build source-instance status rows without reaching into the GitHub persistence model.
 *
 * @property repositoryId Internal repository connection identifier.
 * @property owner Repository owner ("owner" part of "owner/name").
 * @property name Repository name ("name" part of "owner/name").
 * @property status Stable source-status vocabulary value, for example `CONNECTED` or `DISABLED`.
 * @property enabled Whether the source is currently enabled for ingestion.
 * @property lastCommitsSyncAt Last successful commits sync, or `null` when never synced.
 * @property lastIssuesSyncAt Last successful issues sync, or `null` when never synced.
 * @property lastPullRequestsSyncAt Last successful pull-requests sync, or `null` when never synced.
 */
data class GithubSourceInstanceDto(
    val repositoryId: UUID,
    val owner: String,
    val name: String,
    val status: String,
    val enabled: Boolean,
    val lastCommitsSyncAt: Instant?,
    val lastIssuesSyncAt: Instant?,
    val lastPullRequestsSyncAt: Instant?,
)
