package com.sprintstart.sprintstartbackend.github.models.client

import kotlinx.serialization.Serializable

@Serializable
data class GithubCommitsResponse(
    val data: CommitData,
) : PageableResponse<Commit> {
    override val hasNextPage: Boolean
        get() = data.repository.defaultBranchRef.target.history.pageInfo.hasNextPage
    override val endCursor: String?
        get() = data.repository.defaultBranchRef.target.history.pageInfo.endCursor
    override val results: List<Commit>
        get() = data.repository.defaultBranchRef.target.history.nodes
}

@Serializable
data class CommitData(
    val repository: CommitRepository,
)

@Serializable
data class CommitRepository(
    val defaultBranchRef: CommitDefaultBranchRef,
)

@Serializable
data class CommitDefaultBranchRef(
    val target: CommitTarget,
)

@Serializable
data class CommitTarget(
    val history: CommitHistory,
)

@Serializable
data class CommitHistory(
    val pageInfo: CommitPageInfo,
    val nodes: List<Commit>,
)

@Serializable
data class CommitPageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)

@Serializable
data class Commit(
    val oid: String,
    val messageHeadline: String,
    val message: String,
    val committedDate: String,
    val author: CommitAuthor?,
    val changedFilesIfAvailable: Int?,
    val url: String,
)

@Serializable
data class CommitAuthor(
    val name: String?,
    val email: String?,
)
