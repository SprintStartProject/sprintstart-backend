package com.sprintstart.sprintstartbackend.onboarding.service.evidence

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AssignedIssue
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TrackedIssueEvidenceProviderTest {
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val provider = TrackedIssueEvidenceProvider(artifactIngestionApi)

    private val projectId = UUID.randomUUID()
    private val now = Instant.parse("2026-08-01T09:00:00Z")

    private fun member(jiraDisplayName: String? = "Ada Lovelace") = ProjectMember(
        userId = UUID.randomUUID(),
        displayName = "Ada L.",
        githubLogin = null,
        joinedAt = now,
        jiraDisplayName = jiraDisplayName,
    )

    private fun issue(acceptedAt: Instant? = null, returnedCount: Int = 0) = AssignedIssue(
        artifactId = UUID.randomUUID(),
        openedAt = now,
        firstResponseAt = now.plusSeconds(60),
        acceptedAt = acceptedAt,
        returnedCount = returnedCount,
        key = "ONB-42",
    )

    /**
     * The point of the whole slice: a role that never opens a pull request now produces evidence
     * nobody had to vouch for. Attestation was the honest interim, not the destination.
     */
    @Test
    fun `an accepted issue is observed evidence, not attested`() {
        every { artifactIngestionApi.getAssignedIssues(projectId, "Ada Lovelace") } returns
            listOf(issue(acceptedAt = now.plusSeconds(3_600)))

        val contribution = provider.contributionsFor(member(), projectId).single()

        assertThat(contribution.rigor).isEqualTo(Rigor.OBSERVED)
        assertThat(contribution.kind).isEqualTo(ContributionEvidenceKind.TRACKED_ISSUE)
        assertThat(contribution.isAccepted).isTrue()
    }

    /**
     * An issue nobody else accepted is still in flight — including one the hire closed themselves,
     * which `AssignedIssueReader` reports with no acceptance moment at all.
     */
    @Test
    fun `an issue with no acceptance is in flight rather than abandoned`() {
        every { artifactIngestionApi.getAssignedIssues(projectId, "Ada Lovelace") } returns listOf(issue())

        val contribution = provider.contributionsFor(member(), projectId).single()

        assertThat(contribution.state).isEqualTo(ContributionState.IN_FLIGHT)
        assertThat(contribution.isAccepted).isFalse()
    }

    /** Rework travels: "done with no rework" is half the operational definition of autonomy. */
    @Test
    fun `carries the send-back count through`() {
        every { artifactIngestionApi.getAssignedIssues(projectId, "Ada Lovelace") } returns
            listOf(issue(acceptedAt = now.plusSeconds(3_600), returnedCount = 2))

        assertThat(provider.contributionsFor(member(), projectId).single().returnedCount).isEqualTo(2)
    }

    /**
     * ⚠️ No declared name means no attribution is possible — a different fact from having done no
     * work, and read as such everywhere. It must not fall back to the member's SprintStart display
     * name: that is a different string from what Jira renders, and matching on it would attribute
     * somebody's issues by a coincidence of spelling.
     */
    @Test
    fun `a member who declared no tracker name is not looked up at all`() {
        assertThat(provider.contributionsFor(member(jiraDisplayName = null), projectId)).isEmpty()

        verify(exactly = 0) { artifactIngestionApi.getAssignedIssues(any(), any()) }
    }

    @Test
    fun `a blank tracker name is not looked up either`() {
        assertThat(provider.contributionsFor(member(jiraDisplayName = "   "), projectId)).isEmpty()

        verify(exactly = 0) { artifactIngestionApi.getAssignedIssues(any(), any()) }
    }
}
