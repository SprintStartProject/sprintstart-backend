package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs

import java.util.UUID

data class BitbucketPullRequestFetchedEvent(
    val transactionId: UUID,
    val title: String,
    val state: String,
    val authorId: String?,
    val authorNickname: String?,
    val authorDisplayName: String?,
    val createdOn: String,
    val updatedOn: String,
    val description: String?,
    val closedOn: String?,
    val mergeCommitHash: String?,
    val participants: List<PrParticipant>,
    val sourceUrl: String,
    val comments: List<PrComment>,
)

data class PrParticipant(
    val authorId: String?,
    val nickname: String?,
    val displayName: String?,
    val role: String?,
    val approved: Boolean,
    val participatedOn: String?,
    val state: String?,
)

data class PrComment(
    val id: Int,
    val createdOn: String,
    val updatedOn: String?,
    val content: String?,
    val authorId: String?,
    val nickname: String?,
    val displayName: String?,
    val deleted: Boolean,
)
