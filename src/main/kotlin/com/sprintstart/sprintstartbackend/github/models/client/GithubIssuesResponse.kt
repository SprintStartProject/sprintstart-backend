package com.sprintstart.sprintstartbackend.github.models.client

import kotlinx.serialization.Serializable

@Serializable
data class GithubIssuesResponse(
    val data: IssuesData,
) : PageableResponse<Issue> {
    override val hasNextPage: Boolean
        get() = data.repository.issues.pageInfo.hasNextPage
    override val endCursor: String?
        get() = data.repository.issues.pageInfo.endCursor
    override val results: List<Issue>
        get() = data.repository.issues.nodes
}

@Serializable
data class IssuesData(
    val repository: IssuesRepository,
)

@Serializable
data class IssuesRepository(
    val issues: IssueConnection,
)

@Serializable
data class IssueConnection(
    val pageInfo: IssuesPageInfo,
    val nodes: List<Issue>,
)

@Serializable
data class IssuesPageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)

@Serializable
data class Issue(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String?,
    val createdAt: String,
    val closedAt: String,
    val url: String,
    val author: IssueAuthor?,
    val labels: IssueLabels,
    val assignees: IssueAssignees,
    val comments: IssueComments,
)

@Serializable
data class IssueAuthor(
    val login: String,
)

@Serializable
data class IssueLabels(
    val nodes: List<IssueLabel>,
)

@Serializable
data class IssueLabel(
    val name: String,
)

@Serializable
data class IssueAssignees(
    val nodes: List<IssueAssignee>,
)

@Serializable
data class IssueAssignee(
    val login: String,
)

@Serializable
data class IssueComments(
    val nodes: List<IssueComment>,
)

@Serializable
data class IssueComment(
    val body: String,
    val author: IssueAuthor?,
    val createdAt: String,
)
