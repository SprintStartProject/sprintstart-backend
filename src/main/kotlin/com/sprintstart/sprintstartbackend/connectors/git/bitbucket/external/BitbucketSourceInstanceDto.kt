package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external

import java.time.Instant
import java.util.UUID

/**
 * Module-facing view of a connected Bitbucket repository, exposing only the fields other modules
 * need to build source-instance status rows without reaching into the Bitbucket persistence model.
 *
 * Never carries a credential: a connection stores its Atlassian credential by name, and that name
 * has no meaning to a caller that cannot resolve it anyway.
 *
 * Mirrors the GitHub connector's `GithubSourceInstanceDto`, with the fields Bitbucket actually
 * persists. There is no commit-sync timestamp to expose — files and commits are tracked by revision
 * alone — and Bitbucket has no issue tracker, so the status view reports both as unsynced.
 *
 * @property repositoryId Internal repository connection identifier, the key an ingestion run is
 *           stored under.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property sourceUrl The Bitbucket Cloud browser URL of the repository.
 * @property status Stable source-status vocabulary value, for example `CONNECTED` or `DISABLED`.
 * @property enabled Whether the source is currently enabled for ingestion.
 * @property lastPullRequestsSyncAt When the last successful pull-request fetch started, or `null`
 *           when pull requests have never been read.
 */
data class BitbucketSourceInstanceDto(
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val sourceUrl: String,
    val status: String,
    val enabled: Boolean,
    val lastPullRequestsSyncAt: Instant?,
)
