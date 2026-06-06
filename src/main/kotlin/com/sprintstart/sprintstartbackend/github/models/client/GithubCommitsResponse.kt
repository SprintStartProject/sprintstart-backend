package com.sprintstart.sprintstartbackend.github.models.client

import kotlinx.serialization.Serializable

@Serializable
data class GithubCommitsResponse(
    val data: GhRepository,
) : PageableResponse<Commit> {
    override val hasNextPage: Boolean
        get() = data.repository.defaultBranchRef.target.history.pageInfo.hasNextPage
    override val endCursor: String?
        get() = data.repository.defaultBranchRef.target.history.pageInfo.endCursor
    override val results: List<Commit>
        get() = data.repository.defaultBranchRef.target.history.nodes
}

@Serializable
data class GhRepository(
    val repository: Repository,
)

@Serializable
data class Repository(
    val defaultBranchRef: DefaultBranchRef
)

@Serializable
data class DefaultBranchRef(
    val target: Target
)

@Serializable
data class Target(
    val history: History,
)

@Serializable
data class History(
    val pageInfo: PageInfo,
    val nodes: List<Commit>
)

@Serializable
data class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)

@Serializable
data class Commit(
    val oid: String,
    val messageHeadline: String,
    val committedDate: String,
    val author: CommitAuthor?,
    val url: String,
)

@Serializable
data class CommitAuthor(
    val name: String?,
    val email: String?,
)