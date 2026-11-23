package com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql

import kotlinx.serialization.Serializable

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

@Serializable
data class PrNode(
    val number: Int,
    val id: String,
    val title: String,
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
    val author: GithubActor?,
    val labels: LabelsConnection?,
    val reviews: ReviewsConnection?,
    val comments: CommentsConnection?,
    val reviewThreads: ReviewThreadsConnection?,
    val statusCheckRollup: StatusCheckRollup?,
    val files: PullRequestFilesConnection?,
    val commits: PullRequestCommitsConnection?,
)

@Serializable
data class GithubActor(
    val login: String,
)

@Serializable
data class AssigneesCollection(
    val nodes: List<GithubActor>?,
)

@Serializable
data class LabelNode(
    val name: String,
)

@Serializable
data class LabelsConnection(
    val nodes: List<LabelNode>,
)

@Serializable
data class ReviewNode(
    val body: String?,
    val state: String,
    /** When the review was submitted; needed to measure how long a hire waited for a response. */
    val submittedAt: String? = null,
    val author: GithubActor?,
)

@Serializable
data class ReviewsConnection(
    val nodes: List<ReviewNode>?,
)

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

@Serializable
data class StatusCheckRollup(
    val state: String,
)

@Serializable
data class PullRequestFileNode(
    val path: String,
)

@Serializable
data class PullRequestFilesConnection(
    val nodes: List<PullRequestFileNode>?,
)

@Serializable
data class CommitMessage(
    val message: String,
)

@Serializable
data class PullRequestCommitNode(
    val commit: CommitMessage,
)

@Serializable
data class PullRequestCommitsConnection(
    val nodes: List<PullRequestCommitNode>?,
)
