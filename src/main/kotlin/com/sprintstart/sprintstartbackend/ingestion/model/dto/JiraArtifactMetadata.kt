package com.sprintstart.sprintstartbackend.ingestion.model.dto

import java.time.Instant

/**
 * Wrapper for all Jira-specific metadata that is stored as JSON in the artifact's metadata column.
 */
data class JiraArtifactMetadataWrapper(
    val issueType: JiraIssueType,
    val issueKey: String,
    val statusName: String,
    val statusDescription: String,
    val statusCategory: String,
    val createdBy: JiraAuthor,
    val reportedBy: JiraAuthor,
    val assignee: JiraAuthor?,
    val project: JiraProject,
    val history: JiraIssueHistory,
    val comments: List<JiraIssueComment>,
) : ArtifactMetadata

data class JiraProject(
    val key: String,
    val name: String,
    val projectTypeKey: String,
) : ArtifactMetadata

data class JiraIssueType(
    val name: String,
    val description: String,
) : ArtifactMetadata

data class JiraIssueComment(
    val author: JiraAuthor,
    val content: String,
) : ArtifactMetadata

data class JiraIssueHistory(
    val historyItems: List<JiraIssueHistoryItem>,
) : ArtifactMetadata

data class JiraIssueHistoryItem(
    val author: JiraAuthor,
    val createdAt: Instant,
    val items: List<JiraIssueHistorySubitem>,
)

data class JiraIssueHistorySubitem(
    val field: String,
    val fieldtype: String,
    val from: String,
    val to: String,
)

data class JiraAuthor(
    val displayName: String,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
