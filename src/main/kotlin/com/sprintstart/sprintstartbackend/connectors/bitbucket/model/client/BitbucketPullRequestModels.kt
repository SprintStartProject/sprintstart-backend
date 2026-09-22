package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Bitbucket pull request as returned by the collection and single-resource endpoints.
 *
 * A merged or declined pull request keeps [createdOn] and [updatedOn], and additionally reports
 * [closedOn]; [mergeCommit] is only present for merged pull requests.
 *
 * Review state lives in [participants] rather than in a dedicated review resource: Bitbucket has no
 * `reviews` collection, so a reviewer's verdict is attached to the pull request itself. That is
 * convenient — approval state and approval time arrive with the pull request and cost no extra
 * request — but it is also *current state only*. See [BitbucketParticipant].
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
    val participants: List<BitbucketParticipant> = emptyList(),
    val links: BitbucketLinks? = null,
) {
    val url: String?
        get() = links?.html?.href
}

/**
 * One person Bitbucket attached to a pull request, with the role they hold and the verdict they gave.
 *
 * This is Bitbucket's substitute for GitHub's review resource, which is why it is read even though
 * nothing stores it yet: it is the cheapest source of review data, available on the pull request
 * already being fetched.
 *
 * [state] is a *current* verdict (`approved` or `changes_requested`) and deliberately not a history.
 * A reviewer changing their mind overwrites it, so "how many times was a change requested" — which
 * the GitHub mapper can answer from timestamped review events — cannot be counted from this type.
 * Anything history-shaped has to come from the comment collection instead.
 *
 * [participatedOn] is the timestamp of the participant's most recent action, the closest available
 * proxy for when they responded. Compare participants to the pull request author by [BitbucketAccount.accountId]
 * rather than by nickname, which is neither unique nor stable.
 */
@Serializable
data class BitbucketParticipant(
    val user: BitbucketAccount? = null,
    val role: String? = null,
    val approved: Boolean = false,
    @SerialName("participated_on")
    val participatedOn: String? = null,
    val state: String? = null,
)

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

/**
 * The body of a comment, in the two representations this application reads.
 *
 * [raw] is the text exactly as the author wrote it and [markup] names the markup language that text
 * is written in (`markdown`, `plaintext`, `creole`, ...) — [markup] is a format *label*, not
 * rendered content. Ingestion stores [raw] because it is what the author actually wrote; the
 * rendered form is deliberately not modelled, since it is the bulky variant, it is never what the
 * pipeline stores, and [PullRequestComment] is only ever read for response timing and text.
 */
@Serializable
data class BitbucketCommentContent(
    val raw: String? = null,
    val markup: String? = null,
)
