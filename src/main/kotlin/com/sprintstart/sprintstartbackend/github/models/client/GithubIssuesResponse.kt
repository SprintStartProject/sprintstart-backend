package com.sprintstart.sprintstartbackend.github.models.client

import kotlinx.serialization.Serializable

@Serializable
data class GithubIssuesResponse(
    val data: IRepository
) : PageableResponse<Issue> {
    override val hasNextPage: Boolean
        get() = data.repository.issues.pageInfo.hasNextPage
    override val endCursor: String?
        get() = data.repository.issues.pageInfo.endCursor
    override val results: List<Issue>
        get() = data.repository.issues.nodes
}

@Serializable
data class IRepository(
    val repository: IssuesRepository,
)

@Serializable
data class IssuesRepository(
    val issues: Issues
)

@Serializable
data class Issues(
    val pageInfo: PageInfo,
    val nodes: List<Issue>
)

@Serializable
data class Issue(
    val number: Int,
    val title: String,
    val state: String,
    val createdAt: String,
    val closedAt: String?,
    val url: String,
    val author: IssueAuthor?,
    val labels: LabelConnection,
)

@Serializable
data class IssueAuthor(
    val login: String,
)

@Serializable
data class LabelConnection(
    val nodes: List<Label>,
)

@Serializable
data class Label(
    val name: String,
)