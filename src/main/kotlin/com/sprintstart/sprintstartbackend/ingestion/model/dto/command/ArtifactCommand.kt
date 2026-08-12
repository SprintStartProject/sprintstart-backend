package com.sprintstart.sprintstartbackend.ingestion.model.dto.command

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraArtifactMetadataWrapper
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraAuthor
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueComment
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistory
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueType
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraProject
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import java.time.Instant
import java.util.UUID

sealed interface ArtifactCommand

data class GithubArtifactCommand(
    val ingestionRunId: UUID,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    val title: String?,
    val bodyText: String?,
    val mime: String?,
    val language: String?,
    val createdAtSource: Instant?,
    val updatedAtSource: Instant?,
    val hash: String?,
    val metadata: GithubArtifactMetadata,
    // Only populated for ArtifactType.ISSUE today; null/empty for other GitHub artifact types.
    val state: String? = null,
    val labels: List<String> = emptyList(),
    /**
     * GitHub login of whoever authored this artifact at the source, lower-cased.
     *
     * Only ISSUE and PULL_REQUEST carry one: those come from the GraphQL API, which returns a real
     * `author.login`. Commits are parsed out of `git log --pretty=%an`, which is a *git author
     * name* ("Ada Lovelace"), not a GitHub account -- treating it as a login would attribute work
     * to the wrong person. Files have no single author at all.
     */
    val authorLogin: String? = null,
    /** Merge time for pull requests; null for every other artifact type. */
    val mergedAtSource: Instant? = null,
    /** First response by someone other than the author; pull requests only. */
    val firstResponseAtSource: Instant? = null,
    /** Reviews by someone other than the author that asked for changes; pull requests only. */
    val changesRequestedCount: Int = 0,
) : ArtifactCommand

data class JiraArtifactCommand(
    val ingestionRunId: UUID,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val sourceUrl: String,
    val artifactType: ArtifactType,
    val issueType: JiraIssueType,
    val issueId: String,
    val issueKey: String,
    val summary: String,
    val description: String,
    val createdBy: JiraAuthor,
    val reportedBy: JiraAuthor,
    val assignee: JiraAuthor?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val project: JiraProject,
    val history: JiraIssueHistory,
    val comments: List<JiraIssueComment>,
    val statusName: String,
    val statusDescription: String,
    val statusCategory: String,
    val projectIds: Set<UUID> = emptySet(),
) : ArtifactCommand {
    /**
     * The issue's open/closed state in the vocabulary `Artifact.state` uses.
     *
     * Jira has no open/closed flag — a board's statuses are whatever a team typed — but every
     * status belongs to one of Jira's own three *categories*, and `Done` is the only one that means
     * finished. Folding on the category rather than the name is what makes this work for a board
     * whose done column is called "Shipped" or "Akzeptiert".
     *
     * ⚠️ This is what makes a Jira issue visible to starter-work mining at all: the miner's
     * candidate filter is `state == "OPEN"`, so an issue with no state was skipped in silence.
     */
    fun toState(): String = if (statusCategory.equals(DONE_CATEGORY, ignoreCase = true)) "CLOSED" else "OPEN"

    /**
     * Whether somebody is already on this issue.
     *
     * Definite in both directions here, unlike GitHub's, because Jira's assignee field is ingested:
     * an unassigned issue is genuinely `false`, not unknown. That is what lets starter-work mining
     * withhold taken work without withholding everything it cannot see.
     */
    fun toHasAssignee(): Boolean = assignee != null

    private companion object {
        const val DONE_CATEGORY = "Done"
    }

    fun toMetadata(): JiraArtifactMetadataWrapper = JiraArtifactMetadataWrapper(
        issueType = issueType,
        issueKey = issueKey,
        statusName = statusName,
        statusDescription = statusDescription,
        statusCategory = statusCategory,
        createdBy = createdBy,
        reportedBy = reportedBy,
        assignee = assignee,
        project = project,
        history = history,
        comments = comments,
    )
}

data class UploadArtifactCommand(
    val ingestionRunId: UUID,
    val projectId: UUID,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val artifactType: ArtifactType,
    val title: String?,
    val content: String?,
    val mime: String?,
    val language: String?,
    val createdAtSource: Instant?,
    val updatedAtSource: Instant?,
    val hash: String?,
    val metadata: UploadArtifactMetadata,
) : ArtifactCommand

data class ArtifactFailedCommand(
    val transactionId: UUID,
    val sourceId: String?,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    val reason: String,
    val metadata: ArtifactMetadata,
) : ArtifactCommand
