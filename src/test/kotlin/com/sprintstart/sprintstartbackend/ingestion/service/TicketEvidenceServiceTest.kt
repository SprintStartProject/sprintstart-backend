package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.CommitTicket
import com.sprintstart.sprintstartbackend.ingestion.model.dto.CommitMessage
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraAuthor
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueComment
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistory
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistoryItem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueType
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraProject
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.JiraArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IssueActivity
import com.sprintstart.sprintstartbackend.ingestion.repository.CommitMessageRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IssueActivityRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TicketEvidenceServiceTest {
    private val activities = mockk<IssueActivityRepository>(relaxed = true)
    private val messages = mockk<CommitMessageRepository>()
    private val service = TicketEvidenceService(activities, messages)

    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val changed = Instant.parse("2026-01-05T00:00:00Z")
    private val commented = Instant.parse("2026-01-07T00:00:00Z")
    private val updated = Instant.parse("2026-01-10T00:00:00Z")

    private fun person(name: String, id: String? = name.lowercase()) =
        JiraAuthor(displayName = name, active = true, createdAt = created, updatedAt = created, accountId = id)

    private val issue = Artifact(
        sourceSystem = SourceSystem.JIRA,
        sourceId = "10042",
        sourceUrl = null,
        artifactType = ArtifactType.ISSUE,
        title = "Retry the login",
        content = "",
        mime = null,
        language = null,
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.JIRA,
            status = IngestionRunStatus.COMPLETED,
        ),
        hash = null,
    )

    private fun command() = JiraArtifactCommand(
        ingestionRunId = UUID.randomUUID(),
        sourceSystem = SourceSystem.JIRA,
        sourceId = "instance-1",
        sourceUrl = "https://example.test/browse/APP-42",
        artifactType = ArtifactType.ISSUE,
        issueType = JiraIssueType(name = "Task", description = ""),
        issueId = "10042",
        issueKey = "APP-42",
        summary = "Retry the login",
        description = "",
        createdBy = person("Ada"),
        reportedBy = person("Ada"),
        assignee = person("Ben"),
        createdAt = created,
        updatedAt = updated,
        project = JiraProject(key = "APP", name = "App", projectTypeKey = "software"),
        history = JiraIssueHistory(listOf(JiraIssueHistoryItem(person("Cy"), changed, emptyList()))),
        comments = listOf(
            JiraIssueComment(person("Dee"), "Works for me", commented),
            JiraIssueComment(person("Eve", id = null), "Old comment"),
        ),
        statusName = "Done",
        statusDescription = "",
        statusCategory = "done",
    )

    @Test
    fun `everybody who worked on a ticket is recorded once per moment, in place of the old record`() {
        val saved = slot<List<IssueActivity>>()
        every { activities.saveAll(capture(saved)) } answers { saved.captured }

        service.record(issue, command())

        verifyOrder {
            activities.deleteAllByArtifactId(issue.id)
            activities.saveAll(any<List<IssueActivity>>())
        }
        assertThat(saved.captured.map { Triple(it.displayName, it.accountId, it.at) }).containsExactly(
            Triple("Ada", "ada", created),
            Triple("Ben", "ben", updated),
            Triple("Cy", "cy", changed),
            Triple("Dee", "dee", commented),
            Triple("Eve", null, updated),
        )
        assertThat(saved.captured.map { it.issueKey }).containsOnly("APP-42")
        assertThat(saved.captured.map { it.artifact }).containsOnly(issue)
    }

    @Test
    fun `the tickets a commit names are found in its message, each once`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val project = ProjectId(UUID.randomUUID())
        every { messages.findMessages(project.value) } returns listOf(
            CommitMessage(first, "APP-42: retry the login\n\nAlso fixes APP-7 and APP-42 again"),
            CommitMessage(second, "Bump app-1 and X-1 and UTF-8 and nothing"),
            CommitMessage(UUID.randomUUID(), null),
        )

        assertThat(service.commitTickets(project)).containsExactly(
            CommitTicket(first, "APP-42"),
            CommitTicket(first, "APP-7"),
            CommitTicket(second, "UTF-8"),
        )
    }
}
