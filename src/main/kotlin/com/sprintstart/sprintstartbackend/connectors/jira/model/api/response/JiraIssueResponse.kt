package com.sprintstart.sprintstartbackend.connectors.jira.model.api.response

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.serializer.CustomAdfDeserializer
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.serializer.CustomOffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class JiraIssueResponse(
    val id: String,
    val key: String,
    val changelog: JiraIssueChangelog,
    val fields: JiraIssueFields,
)

@Serializable
data class JiraIssueChangelog(
    val histories: List<JiraIssueChangelogHistory>,
)

@Serializable
data class JiraIssueChangelogHistory(
    val author: JiraAuthor,
    @Serializable(with = CustomOffsetDateTimeSerializer::class)
    val created: OffsetDateTime,
    val items: List<JiraIssueChangelogHistoryItem>,
)

@Serializable
data class JiraIssueChangelogHistoryItem(
    val field: String,
    val fieldtype: String,
    val fromString: String? = null,
    val toString: String? = null,
)

@Serializable
data class JiraIssueFields(
    val summary: String,
    val issueType: JiraIssueType? = null,
    val creator: JiraAuthor,
    // val components
    // val subtasks
    @Serializable(with = CustomOffsetDateTimeSerializer::class)
    val created: OffsetDateTime,
    val description: JiraIssueDescription? = null,
    val project: JiraIssueProject,
    val reporter: JiraAuthor,
    // val resolution
    // val timetracking
    // val labels
    // val environment
    // val versions
    // val duedate
    // val resolutiondate
    val comment: JiraIssueCommentField,
    val assignee: JiraAuthor?,
    @Serializable(with = CustomOffsetDateTimeSerializer::class)
    val updated: OffsetDateTime,
    val status: JiraIssueStatus,
)

@Serializable
data class JiraIssueType(
    val name: String,
    val description: String,
)

@Serializable
data class JiraIssueDescription(
    val type: String,
    val version: Int,
    @Serializable(with = CustomAdfDeserializer::class)
    val content: String,
)

@Serializable
data class JiraIssueProject(
    val key: String,
    val name: String,
    val projectTypeKey: String,
)

@Serializable
data class JiraIssueStatus(
    val name: String,
    val description: String,
    val category: JiraIssueStatusCategory? = null,
)

@Serializable
data class JiraIssueStatusCategory(
    val key: String,
    val name: String,
)

@Serializable
data class JiraIssueCommentField(
    val comments: List<JiraIssueComment>,
)

@Serializable
data class JiraIssueComment(
    val author: JiraAuthor,
    val body: JiraIssueCommentBody,
)

@Serializable
data class JiraAuthor(
    val displayName: String,
    val active: Boolean,
    @Serializable(with = CustomOffsetDateTimeSerializer::class)
    val created: OffsetDateTime? = null,
    @Serializable(with = CustomOffsetDateTimeSerializer::class)
    val updated: OffsetDateTime? = null,
)

@Serializable
data class JiraIssueCommentBody(
    @Serializable(with = CustomAdfDeserializer::class)
    val content: String,
)
