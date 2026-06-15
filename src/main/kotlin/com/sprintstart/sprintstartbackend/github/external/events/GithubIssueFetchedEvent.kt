package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID

/**
 * Event triggered when a GitHub issue is fetched.
 *
 * This event encapsulates detailed information about a GitHub issue that has been
 * retrieved from a repository. It includes metadata such as the issue's title, body,
 * state, timestamps, associated labels, and related comments. The event is primarily
 * used to propagate issue-related data throughout the system in workflows or
 * processing pipelines.
 *
 * @property transactionId Unique identifier for the transaction associated with this event.
 * @property number The number of the GitHub issue within the repository.
 * @property title The title of the GitHub issue.
 * @property body The optional body or description of the GitHub issue.
 * @property state The current state of the GitHub issue (e.g., open, closed).
 * @property createdAt The timestamp indicating when the GitHub issue was created.
 * @property closedAt The optional timestamp indicating when the GitHub issue was closed.
 * @property url The URL linking to the GitHub issue in the repository.
 * @property author The optional username or identifier of the author who created the issue.
 * @property labels A list of labels associated with the GitHub issue.
 * @property assignees A list of assignees associated with the GitHub issue.
 * @property comments A list of comments related to the GitHub issue.
 */
data class GithubIssueFetchedEvent(
    val transactionId: UUID,
    val number: Int,
    val title: String,
    val body: String?,
    val state: String?,
    val createdAt: String,
    val closedAt: String?,
    val url: String,
    val author: String?,
    val labels: List<String>,
    val assignees: List<String>,
    val comments: List<GithubIssueComment>,
)

data class GithubIssueComment(
    val body: String,
    val author: String?,
    val createdAt: String,
)
