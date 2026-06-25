package com.sprintstart.sprintstartbackend.github.external.events.pullrequests

import java.util.UUID

/**
 * Event triggered when a GitHub pull request is fetched.
 *
 * This event encapsulates detailed information about a GitHub pull request that has been
 * retrieved from a repository. It provides metadata such as the pull request's number,
 * body description, state, timestamps, URL, author, associated labels, reviews, comments,
 * and review threads. The event serves to propagate pull request-related data throughout
 * the system in workflows and processing pipelines.
 *
 * @property transactionId Unique identifier for the transaction associated with this event.
 * @property number The number of the GitHub pull request within the repository.
 * @property body The optional body or description of the GitHub pull request.
 * @property state The current state of the GitHub pull request (e.g., open, closed, merged).
 * @property createdAt The timestamp indicating when the GitHub pull request was created.
 * @property mergedAt The optional timestamp indicating when the GitHub pull request was merged.
 * @property url The URL linking to the GitHub pull request in the repository.
 * @property author The optional username or identifier of the author who created the pull request.
 * @property labels A list of labels associated with the GitHub pull request.
 * @property reviews A list of reviews associated with the GitHub pull request.
 * @property comments A list of comments related to the GitHub pull request.
 * @property reviewThreads A list of review threads associated with the GitHub pull request.
 */
data class GithubPullRequestFetchedEvent(
    val transactionId: UUID,
    val number: Int,
    val body: String?,
    val state: String,
    val createdAt: String,
    val mergedAt: String?,
    val url: String,
    val author: String?,
    val labels: List<String>?,
    val reviews: List<GithubPullRequestReview>?,
    val comments: List<GithubPullRequestComment>?,
    val reviewThreads: List<GithubPullRequestReviewThread>?,
)

data class GithubPullRequestReview(
    val body: String?,
    val state: String,
    val author: String?,
)

data class GithubPullRequestComment(
    val body: String,
    val author: String?,
    val createdAt: String,
)

data class GithubPullRequestReviewThread(
    val comments: List<GithubPullRequestReviewThreadComment>,
)

data class GithubPullRequestReviewThreadComment(
    val body: String,
    val author: String?,
    val path: String,
)
