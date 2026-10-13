package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs

import java.util.UUID

/**
 * One pull request of a Bitbucket repository that was fetched and should be ingested.
 *
 * The event carries the repository identity that every other Bitbucket artifact event carries, so
 * the ingestion side can scope the pull request to the projects the repository is linked to without
 * resolving the author or the participants first. The [number] is Bitbucket's pull request id; it
 * is the stable, human-visible part of the source identity the artifact is deduplicated on.
 *
 * @property transactionId The ingestion run this pull request belongs to.
 * @property repositoryId The connected repository the pull request belongs to.
 * @property workspace The Bitbucket workspace owning the repository.
 * @property slug The repository's slug within [workspace].
 * @property number Bitbucket's pull request id, unique per repository.
 * @property title The pull request title.
 * @property state One of Bitbucket's pull request states (`OPEN`, `MERGED`, `DECLINED`,
 *           `SUPERSEDED`).
 * @property authorId The Bitbucket account id of the author, or `null` when the account is gone.
 * @property authorNickname The author's Bitbucket handle, or `null`.
 * @property authorDisplayName The author's display name, or `null`.
 * @property createdOn When the pull request was opened, ISO 8601.
 * @property updatedOn When the pull request was last touched at the source, ISO 8601.
 * @property description The pull request description, or `null` when it has none.
 * @property closedOn When the pull request was merged or declined, ISO 8601, or `null` while open.
 * @property mergeCommitHash The hash of the merge commit, or `null` when the pull request was never
 *           merged.
 * @property participants Everybody the pull request lists as a participant, reviewers included.
 * @property sourceUrl A link to the pull request on Bitbucket.
 * @property comments Every comment on the pull request, deleted ones included and marked as such.
 */
data class BitbucketPullRequestFetchedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
    val number: Int,
    val title: String,
    val state: String,
    val authorId: String?,
    val authorNickname: String?,
    val authorDisplayName: String?,
    val createdOn: String,
    val updatedOn: String,
    val description: String?,
    val closedOn: String?,
    val mergeCommitHash: String?,
    val participants: List<PrParticipant>,
    val sourceUrl: String,
    val comments: List<PrComment>,
)

data class PrParticipant(
    val authorId: String?,
    val nickname: String?,
    val displayName: String?,
    val role: String?,
    val approved: Boolean,
    val participatedOn: String?,
    val state: String?,
)

data class PrComment(
    val id: Int,
    val createdOn: String,
    val updatedOn: String?,
    val content: String?,
    val authorId: String?,
    val nickname: String?,
    val displayName: String?,
    val deleted: Boolean,
)
