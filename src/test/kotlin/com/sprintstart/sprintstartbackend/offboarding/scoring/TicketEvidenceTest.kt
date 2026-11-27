package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.CommitTicket
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IssueActivityReading
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TicketEvidenceTest {
    private val project = ProjectId(UUID.randomUUID())
    private val api = mockk<ContributionEvidenceApi>()
    private val source = TicketEvidence(api)
    private val fix = UUID.randomUUID()
    private val chore = UUID.randomUUID()

    private fun day(n: Long): Instant = NOW.minusSeconds(n * 86_400)

    private fun change(path: String, commit: UUID?) =
        FileContribution(REPOSITORY, path, null, "dev@x.org", day(3), commit)

    private fun given(
        tickets: List<CommitTicket>,
        activity: List<IssueActivityReading>,
        changes: List<FileContribution>,
    ) {
        every { api.commitTickets(project) } returns tickets
        every { api.issueActivities(project, NOW) } returns activity
        every { api.fileContributions(project, NOW) } returns changes
    }

    @Test
    fun `the people behind a ticket are credited with the files its commits changed`() {
        given(
            tickets = listOf(CommitTicket(fix, "APP-1")),
            activity = listOf(
                IssueActivityReading("APP-1", "Acc-Ada", "Ada", day(9)),
                IssueActivityReading("APP-1", null, " Ben ", day(5)),
                IssueActivityReading("APP-2", "acc-eve", "Eve", day(4)),
            ),
            changes = listOf(change("src/Login.kt", fix), change("src/Other.kt", chore), change("README.md", null)),
        )

        val history = source.histories(project, NOW).single()

        assertThat(history.path).isEqualTo("src/Login.kt")
        assertThat(history.touches.map { it.person.value }).containsExactly("atlassian:acc-ada", "jira:ben")
        assertThat(history.touches).allMatch { it.source == SourceId.TICKETS && !it.founding }
    }

    @Test
    fun `two commits of one ticket on one file credit each moment once`() {
        val again = UUID.randomUUID()
        given(
            tickets = listOf(CommitTicket(fix, "APP-1"), CommitTicket(again, "APP-1")),
            activity = listOf(IssueActivityReading("APP-1", "acc-ada", "Ada", day(9))),
            changes = listOf(change("src/Login.kt", fix), change("src/Login.kt", again)),
        )

        assertThat(source.histories(project, NOW).single().touches).hasSize(1)
    }

    @Test
    fun `a project whose commits name no ticket reads nothing else`() {
        every { api.commitTickets(project) } returns emptyList()

        assertThat(source.histories(project, NOW)).isEmpty()
        verify(exactly = 0) { api.fileContributions(any(), any()) }
    }

    @Test
    fun `tickets count only when the profile gives them a weight`() {
        assertThat(WeightProfile().weightOf(SourceId.TICKETS)).isZero()
    }
}
