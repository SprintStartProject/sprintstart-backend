package com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql

import kotlinx.serialization.Serializable

// Used for your initial Issues query
@Serializable
data class Issue(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val createdAt: String,
    val updatedAt: String,
    val closedAt: String?,
    val url: String,
    val author: GithubActor?,
    val labels: LabelsConnection?,
    val assignees: AssigneesCollection?,
    val comments: CommentsConnection?,
)

// Used for Step 1 of the new PR architecture
@Serializable
data class PrNode(
    val number: Int,
    val id: String,
    val title: String,
)

// Used for Step 2 (and your original query).
// Notice how it uses the Connection classes defined above to match the JSON shape.
@Serializable
data class PullRequest(
    val number: Int,
    val title: String,
    val body: String?,
    val state: String,
    val createdAt: String,
    val mergedAt: String?,
    val url: String,
    val author: GithubActor?,
    val labels: LabelsConnection?,
    val reviews: ReviewsConnection?,
    val comments: CommentsConnection?,
    val reviewThreads: ReviewThreadsConnection?,
)

@Serializable
data class GithubActor(
    val login: String,
)

@Serializable
data class AssigneesCollection(
    val nodes: List<GithubActor>?,
)

// --- Labels ---
@Serializable
data class LabelNode(
    val name: String,
)

@Serializable
data class LabelsConnection(
    val nodes: List<LabelNode>,
)

// --- Reviews ---
@Serializable
data class ReviewNode(
    val body: String?,
    val state: String,
    val author: GithubActor?,
)

@Serializable
data class ReviewsConnection(
    val nodes: List<ReviewNode>?,
)

// --- Comments ---
@Serializable
data class CommentNode(
    val body: String,
    val author: GithubActor?,
    val createdAt: String,
)

@Serializable
data class CommentsConnection(
    val nodes: List<CommentNode>?,
)

// --- Threads (Deeply nested) ---
@Serializable
data class ThreadCommentNode(
    val body: String,
    val author: GithubActor?,
    val path: String,
)

@Serializable
data class ThreadCommentsConnection(
    val nodes: List<ThreadCommentNode>?,
)

@Serializable
data class ReviewThreadNode(
    val comments: ThreadCommentsConnection?,
)

@Serializable
data class ReviewThreadsConnection(
    val nodes: List<ReviewThreadNode>?,
)
