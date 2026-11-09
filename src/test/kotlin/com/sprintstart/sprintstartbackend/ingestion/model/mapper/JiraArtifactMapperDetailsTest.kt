package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraAuthor
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueChangelog
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueChangelogHistory
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueChangelogHistoryItem
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueComment
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueCommentBody
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueCommentField
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueDescription
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueFields
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueProject
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueResponse
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueStatus
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

/** The people, history and comments the Jira mapper carries over, and the timestamps it falls back to. */
class JiraArtifactMapperDetailsTest {
    private val mapper = JiraArtifactMapper()

    private val issueCreated = OffsetDateTime.parse("2026-09-01T08:00:00Z")
    private val issueUpdated = OffsetDateTime.parse("2026-09-09T09:00:00Z")
    private val authorCreated = OffsetDateTime.parse("2026-01-01T00:00:00Z")
    private val authorUpdated = OffsetDateTime.parse("2026-02-01T00:00:00Z")
    private val historyCreated = OffsetDateTime.parse("2026-09-05T12:00:00Z")

    private fun bare(name: String) = JiraAuthor(displayName = name, active = true)

    private fun dated(name: String) =
        JiraAuthor(displayName = name, active = false, created = authorCreated, updated = authorUpdated)

    private fun event(
        creator: JiraAuthor = bare("Creator"),
        reporter: JiraAuthor = bare("Reporter"),
        assignee: JiraAuthor? = null,
        histories: List<JiraIssueChangelogHistory> = emptyList(),
        comments: List<JiraIssueComment> = emptyList(),
        issueType: JiraIssueType? = null,
        description: JiraIssueDescription? = null,
    ) = JiraIssueFetchedEvent(
        transactionId = UUID.randomUUID(),
        instanceId = "instance-1",
        instanceUrl = "https://example.test",
        sourceUrl = "https://example.test/browse/ONB-42",
        issue = JiraIssueResponse(
            id = "10042",
            key = "ONB-42",
            changelog = JiraIssueChangelog(histories = histories),
            fields = JiraIssueFields(
                summary = "Run the sprint retro",
                issueType = issueType,
                creator = creator,
                created = issueCreated,
                description = description,
                project = JiraIssueProject(key = "ONB", name = "Onboarding", projectTypeKey = "software"),
                reporter = reporter,
                comment = JiraIssueCommentField(comments = comments),
                assignee = assignee,
                updated = issueUpdated,
                status = JiraIssueStatus(name = "Open", description = "Not started"),
            ),
        ),
    )

    @Test
    fun `an author without dates of their own takes the issue's`() {
        val command = mapper.toCommand(event(creator = bare("Ada"), reporter = bare("Grace"), assignee = bare("Alan")))

        listOf(command.createdBy, command.reportedBy, command.assignee!!).forEach {
            assertThat(it.createdAt).isEqualTo(issueCreated.toInstant())
            assertThat(it.updatedAt).isEqualTo(issueUpdated.toInstant())
        }
        assertThat(command.createdBy.displayName).isEqualTo("Ada")
        assertThat(command.reportedBy.displayName).isEqualTo("Grace")
        assertThat(command.assignee!!.displayName).isEqualTo("Alan")
        assertThat(command.createdBy.active).isTrue()
    }

    @Test
    fun `an author with dates of their own keeps them`() {
        val command = mapper.toCommand(
            event(creator = dated("Ada"), reporter = dated("Grace"), assignee = dated("Alan")),
        )

        listOf(command.createdBy, command.reportedBy, command.assignee!!).forEach {
            assertThat(it.createdAt).isEqualTo(authorCreated.toInstant())
            assertThat(it.updatedAt).isEqualTo(authorUpdated.toInstant())
            assertThat(it.active).isFalse()
        }
    }

    @Test
    fun `an issue nobody is assigned to has no assignee`() {
        val command = mapper.toCommand(event(assignee = null))

        assertThat(command.assignee).isNull()
        assertThat(command.toHasAssignee()).isFalse()
    }

    @Test
    fun `the changelog keeps who changed what and when, with absent values as empty text`() {
        val history = JiraIssueChangelogHistory(
            author = bare("Ada"),
            created = historyCreated,
            items = listOf(
                JiraIssueChangelogHistoryItem(
                    field = "status",
                    fieldtype = "jira",
                    fromString = "Open",
                    toString = "Done",
                ),
                JiraIssueChangelogHistoryItem(field = "labels", fieldtype = "custom"),
            ),
        )

        val command = mapper.toCommand(event(histories = listOf(history)))
        val item = command.history.historyItems.single()

        assertThat(item.author.displayName).isEqualTo("Ada")
        assertThat(item.author.createdAt).isEqualTo(historyCreated.toInstant())
        assertThat(item.author.updatedAt).isEqualTo(historyCreated.toInstant())
        assertThat(item.createdAt).isEqualTo(historyCreated.toInstant())
        assertThat(item.items).hasSize(2)
        assertThat(item.items[0].field).isEqualTo("status")
        assertThat(item.items[0].fieldtype).isEqualTo("jira")
        assertThat(item.items[0].from).isEqualTo("Open")
        assertThat(item.items[0].to).isEqualTo("Done")
        assertThat(item.items[1].from).isEmpty()
        assertThat(item.items[1].to).isEmpty()
    }

    @Test
    fun `a changelog author with dates of their own keeps them`() {
        val history = JiraIssueChangelogHistory(author = dated("Ada"), created = historyCreated, items = emptyList())

        val command = mapper.toCommand(event(histories = listOf(history)))
        val item = command.history.historyItems.single()
        val author = item.author

        assertThat(author.createdAt).isEqualTo(authorCreated.toInstant())
        assertThat(author.updatedAt).isEqualTo(authorUpdated.toInstant())
    }

    @Test
    fun `comments keep their text and author, falling back to the issue's dates`() {
        val comments = listOf(
            JiraIssueComment(author = bare("Ada"), body = JiraIssueCommentBody(content = "first")),
            JiraIssueComment(author = dated("Grace"), body = JiraIssueCommentBody(content = "second")),
        )

        val result = mapper.toCommand(event(comments = comments)).comments

        assertThat(result.map { it.content }).containsExactly("first", "second")
        assertThat(result[0].author.displayName).isEqualTo("Ada")
        assertThat(result[0].author.createdAt).isEqualTo(issueCreated.toInstant())
        assertThat(result[0].author.updatedAt).isEqualTo(issueUpdated.toInstant())
        assertThat(result[1].author.createdAt).isEqualTo(authorCreated.toInstant())
        assertThat(result[1].author.updatedAt).isEqualTo(authorUpdated.toInstant())
    }

    @Test
    fun `the issue type and description come across, with a placeholder type when Jira sends none`() {
        val typed = mapper.toCommand(
            event(
                issueType = JiraIssueType(name = "Bug", description = "Something broke"),
                description = JiraIssueDescription(type = "doc", version = 1, content = "Steps to reproduce"),
            ),
        )
        val untyped = mapper.toCommand(event())

        assertThat(typed.issueType.name).isEqualTo("Bug")
        assertThat(typed.issueType.description).isEqualTo("Something broke")
        assertThat(typed.description).isEqualTo("Steps to reproduce")
        assertThat(untyped.issueType.name).isEqualTo("Unknown")
        assertThat(untyped.description).isEmpty()
    }
}
