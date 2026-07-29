package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueFetchedEvent
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraAuthor
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueComment
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistory
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistoryItem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistorySubitem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueType
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraProject
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.JiraArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import org.springframework.stereotype.Component

@Component
class JiraArtifactMapper {
    fun toCommand(event: JiraIssueFetchedEvent): JiraArtifactCommand {
        return JiraArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.JIRA,
            sourceId = event.instanceId,
            sourceUrl = event.sourceUrl,
            artifactType = ArtifactType.ISSUE,
            issueType = event.getIssueType(),
            issueId = event.issue.id,
            issueKey = event.issue.key,
            summary = event.issue.fields.summary,
            description = event.issue.fields.description
                ?.content ?: "",
            createdBy = event.getCreator(),
            reportedBy = event.getReporter(),
            assignee = event.getAssignee(),
            createdAt = event.issue.fields.created
                .toInstant(),
            updatedAt = event.issue.fields.updated
                .toInstant(),
            project = event.getProject(),
            history = event.getChangelog(),
            comments = event.getComments(),
            statusName = event.issue.fields.status.name,
            statusDescription = event.issue.fields.status.description,
            statusCategory = event.issue.fields.status.category
                ?.name
                ?: event.issue.fields.status.name,
            projectIds = event.projectIds,
        )
    }
}

fun JiraIssueFetchedEvent.getIssueType(): JiraIssueType {
    return this.issue.fields.issueType?.let {
        JiraIssueType(
            name = it.name,
            description = it.description,
        )
    } ?: JiraIssueType(
        name = "Unknown",
        description = "",
    )
}

fun JiraIssueFetchedEvent.getCreator(): JiraAuthor {
    return JiraAuthor(
        displayName = this.issue.fields.creator.displayName,
        active = this.issue.fields.creator.active,
        createdAt = this.issue.fields.creator.created
            ?.toInstant()
            ?: this.issue.fields.created
                .toInstant(),
        updatedAt = this.issue.fields.creator.updated
            ?.toInstant()
            ?: this.issue.fields.updated
                .toInstant(),
    )
}

fun JiraIssueFetchedEvent.getReporter(): JiraAuthor {
    return JiraAuthor(
        displayName = this.issue.fields.reporter.displayName,
        active = this.issue.fields.reporter.active,
        createdAt = this.issue.fields.reporter.created
            ?.toInstant()
            ?: this.issue.fields.created
                .toInstant(),
        updatedAt = this.issue.fields.reporter.updated
            ?.toInstant()
            ?: this.issue.fields.updated
                .toInstant(),
    )
}

fun JiraIssueFetchedEvent.getAssignee(): JiraAuthor? {
    if (this.issue.fields.assignee == null) {
        return null
    }

    return this.issue.fields.assignee.let {
        JiraAuthor(
            displayName = it.displayName,
            active = it.active,
            createdAt = it.created?.toInstant()
                ?: this.issue.fields.created
                    .toInstant(),
            updatedAt = it.updated?.toInstant()
                ?: this.issue.fields.updated
                    .toInstant(),
        )
    }
}

fun JiraIssueFetchedEvent.getProject(): JiraProject {
    return JiraProject(
        key = this.issue.fields.project.key,
        name = this.issue.fields.project.name,
        projectTypeKey = this.issue.fields.project.projectTypeKey,
    )
}

fun JiraIssueFetchedEvent.getChangelog(): JiraIssueHistory {
    return JiraIssueHistory(
        historyItems = this.issue.changelog.histories.map { history ->
            JiraIssueHistoryItem(
                author = JiraAuthor(
                    displayName = history.author.displayName,
                    active = history.author.active,
                    createdAt = history.author.created?.toInstant()
                        ?: history.created.toInstant(),
                    updatedAt = history.author.updated?.toInstant()
                        ?: history.created.toInstant(),
                ),
                createdAt = history.created.toInstant(),
                items = history.items.map {
                    JiraIssueHistorySubitem(
                        field = it.field,
                        fieldtype = it.fieldtype,
                        from = it.fromString ?: "",
                        to = it.toString ?: "",
                    )
                },
            )
        },
    )
}

fun JiraIssueFetchedEvent.getComments(): List<JiraIssueComment> {
    return this.issue.fields.comment.comments.map {
        JiraIssueComment(
            author = JiraAuthor(
                it.author.displayName,
                it.author.active,
                it.author.created?.toInstant()
                    ?: this.issue.fields.created
                        .toInstant(),
                it.author.updated?.toInstant()
                    ?: this.issue.fields.updated
                        .toInstant(),
            ),
            content = it.body.content,
        )
    }
}
