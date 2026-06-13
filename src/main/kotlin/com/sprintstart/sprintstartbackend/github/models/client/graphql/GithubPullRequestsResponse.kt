package com.sprintstart.sprintstartbackend.github.models.client.graphql

import kotlinx.serialization.Serializable

@Serializable
data class GithubPullRequestsResponse(
    val data: PrData,
) : PageableResponse<PullRequest> {
    override val hasNextPage: Boolean
        get() = data.repository.pullRequests.pageInfo.hasNextPage
    override val endCursor: String?
        get() = data.repository.pullRequests.pageInfo.endCursor
    override val results: List<PullRequest>
        get() = data.repository.pullRequests.nodes
}

@Serializable
data class PrData(
    val repository: PrRepository,
)

@Serializable
data class PrRepository(
    val pullRequests: PrConnection,
)

@Serializable
data class PrConnection(
    val pageInfo: PrPageInfo,
    val nodes: List<PullRequest>,
)

@Serializable
data class PrPageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)

@Serializable
data class PullRequest(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val createdAt: String,
    val mergedAt: String?,
    val url: String,
    val author: PrAuthor?,
    val labels: PrLabels?,
    val reviews: PrReviews?,
    val comments: PrComments?,
    val reviewThreads: PrReviewThreads?,
)

@Serializable
data class PrAuthor(
    val login: String,
)

@Serializable
data class PrLabels(
    val nodes: List<PrLabel>,
)

@Serializable
data class PrLabel(
    val name: String,
)

@Serializable
data class PrReviews(
    val nodes: List<PrReview>,
)

@Serializable
data class PrReview(
    val body: String?,
    val state: String,
    val author: PrAuthor?,
)

@Serializable
data class PrComments(
    val nodes: List<PrComment>,
)

@Serializable
data class PrComment(
    val body: String,
    val author: PrAuthor?,
    val createdAt: String,
)

@Serializable
data class PrReviewThreads(
    val nodes: List<PrReviewThread>,
)

@Serializable
data class PrReviewThread(
    val comments: PrReviewThreadComments,
)

@Serializable
data class PrReviewThreadComments(
    val nodes: List<PrReviewThreadComment>,
)

@Serializable
data class PrReviewThreadComment(
    val body: String,
    val author: PrAuthor?,
    val path: String,
)
