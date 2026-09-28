package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraAuthor
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueChangelog
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueCommentField
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueFields
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueProject
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueStatus
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueStatusCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

/**
 * What the mapper puts in `statusCategory`, which is the only thing standing between a finished
 * Jira issue and a pool that keeps offering it.
 *
 * The fold to `OPEN`/`CLOSED` lives in `JiraArtifactCommand.toState()` and is covered by
 * `JiraArtifactProviderServiceTest`; what is checked here is which of Jira's two spellings of a
 * status category the mapper hands it.
 */
class JiraArtifactMapperTest {
    private val mapper = JiraArtifactMapper()

    private val now: OffsetDateTime = OffsetDateTime.parse("2026-09-09T09:00:00Z")

    private fun author() = JiraAuthor(displayName = "Grace Hopper", active = true)

    private fun event(category: JiraIssueStatusCategory?, statusName: String) = JiraIssueFetchedEvent(
        transactionId = UUID.randomUUID(),
        instanceId = "instance-1",
        instanceUrl = "https://example.test",
        sourceUrl = "https://example.test/browse/ONB-42",
        issue = JiraIssueResponse(
            id = "10042",
            key = "ONB-42",
            changelog = JiraIssueChangelog(histories = emptyList()),
            fields = JiraIssueFields(
                summary = "Run the sprint retro",
                creator = author(),
                created = now,
                project = JiraIssueProject(key = "ONB", name = "Onboarding", projectTypeKey = "software"),
                reporter = author(),
                comment = JiraIssueCommentField(comments = emptyList()),
                assignee = null,
                updated = now,
                status = JiraIssueStatus(name = statusName, description = "", category = category),
            ),
        ),
    )

    /**
     * The key, not the name. Jira localizes a category's display name exactly as it does a status'
     * — on a German instance the done category reads "Fertig" — so mapping the name meant every
     * finished issue on a non-English instance was ingested as open, mining kept offering closed
     * work, and `StarterWorkPoolReconciler` never saw a `CLOSED` to go stale on.
     */
    @Test
    fun `a localized done category is carried as its stable key`() {
        val command = mapper.toCommand(event(JiraIssueStatusCategory(key = "done", name = "Fertig"), "Erledigt"))

        assertThat(command.statusCategory).isEqualTo("done")
        assertThat(command.toState()).isEqualTo("CLOSED")
    }

    @Test
    fun `an unfinished category is carried as its stable key`() {
        val command =
            mapper.toCommand(event(JiraIssueStatusCategory(key = "indeterminate", name = "In Arbeit"), "In Arbeit"))

        assertThat(command.statusCategory).isEqualTo("indeterminate")
        assertThat(command.toState()).isEqualTo("OPEN")
    }

    /**
     * `category` is optional on the response, and a response without one leaves nothing better to
     * go on than the status name. That is what the mapper did for every issue before it read the
     * key, so the English case keeps working and no instance is worse off than it was.
     */
    @Test
    fun `a response with no category falls back to the status name`() {
        val command = mapper.toCommand(event(null, "Done"))

        assertThat(command.statusCategory).isEqualTo("Done")
        assertThat(command.toState()).isEqualTo("CLOSED")
    }

    /**
     * The honest limit of that fallback: with no category and a status a team named themselves,
     * nothing in the payload says the issue is finished, and it is ingested as open. Reconciliation
     * reads that as "still open" rather than "unknown", so such a row is never marked stale.
     */
    @Test
    fun `a response with no category and a localized status reads as open`() {
        val command = mapper.toCommand(event(null, "Erledigt"))

        assertThat(command.toState()).isEqualTo("OPEN")
    }
}
