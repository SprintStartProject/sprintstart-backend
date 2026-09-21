package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Bitbucket pull request as returned by the collection and single-resource endpoints.
 *
 * A merged or declined pull request keeps [createdOn] and [updatedOn], and additionally reports
 * [closedOn]; [mergeCommit] is only present for merged pull requests. Bitbucket has no dedicated
 * review or label resources like GitHub, so onboarding metrics that need first-response times are
 * derived from the separate comment collection, not from this type.
 *
 * @param id the pull request's id within its repository, Bitbucket's counterpart to a GitHub PR number.
 * @param state one of OPEN, MERGED, DECLINED, SUPERSEDED.
 * @param url the pull request's human-facing page, derived from the link block.
 */
@Serializable
data class PullRequest(
    val id: Int,
    val title: String,
    val state: String,
    val author: BitbucketAccount? = null,
    @SerialName("created_on")
    val createdOn: String,
    @SerialName("updated_on")
    val updatedOn: String,
    val description: String? = null,
    @SerialName("closed_on")
    val closedOn: String? = null,
    @SerialName("merge_commit")
    val mergeCommit: BitbucketMergeCommit? = null,
    val links: BitbucketLinks? = null,
) {
    val url: String?
        get() = links?.html?.href
}

/** The commit a pull request was merged with; only reported by Bitbucket for merged pull requests. */
@Serializable
data class BitbucketMergeCommit(
    val hash: String,
)

/**
 * A comment on a Bitbucket pull request.
 *
 * Bitbucket returns deleted comments as well, marked with [deleted] and usually without content,
 * so callers deriving response metrics have to filter them out themselves — a deleted comment is
 * not a response. Note that the author field is named `user` here, whereas pull requests name it
 * `author`; Bitbucket is inconsistent between the two endpoints.
 */
@Serializable
data class PullRequestComment(
    val id: Int,
    @SerialName("created_on")
    val createdOn: String,
    @SerialName("updated_on")
    val updatedOn: String? = null,
    val content: BitbucketCommentContent? = null,
    val user: BitbucketAccount? = null,
    val deleted: Boolean = false,
)

/** The rendered variants of a comment's body. */
@Serializable
data class BitbucketCommentContent(
    val raw: String? = null,
    val markup: String? = null,
)
