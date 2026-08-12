package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.service.evidence.PullRequestEvidenceProvider
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The through-line of these tests: **"not accepted" is three different situations, not one.**
 *
 * Work still in flight is waiting on somebody and is the failure the instrumentation exists to
 * catch; work abandoned is finished and waiting on nobody; and a hire with no attributable identity
 * has produced no evidence we can see, which is not the same as having produced nothing. Collapsing
 * any of the three into the others is how a dashboard ends up quietly wrong.
 */
class ContributionServiceTest {
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val service = ContributionService(listOf(PullRequestEvidenceProvider(artifactIngestionApi)))

    private val now: Instant = Instant.parse("2026-07-27T12:00:00Z")
    private val projectId: UUID = UUID.randomUUID()

    private fun daysAgo(days: Long): Instant = now.minus(Duration.ofDays(days))

    private fun member(login: String? = "hire") =
        ProjectMember(UUID.randomUUID(), "A Hire", login, daysAgo(20))

    private fun pullRequest(
        merged: Instant? = null,
        state: String? = "OPEN",
        changesRequested: Int = 0,
    ) = AuthoredPullRequest(
        artifactId = UUID.randomUUID(),
        openedAt = daysAgo(5),
        firstResponseAt = null,
        mergedAt = merged,
        state = state,
        changesRequestedCount = changesRequested,
    )

    private fun authored(vararg pullRequests: AuthoredPullRequest) {
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "hire") } returns
            pullRequests.toList()
    }

    @Test
    fun `a merged pull request is an accepted contribution`() {
        val mergedAt = daysAgo(3)
        authored(pullRequest(merged = mergedAt, state = "MERGED"))

        val contribution = service.forHire(member(), projectId).single()

        assertEquals(ContributionState.ACCEPTED, contribution.state)
        assertEquals(mergedAt, contribution.acceptedAt)
        assertTrue(contribution.isAccepted)
        assertFalse(contribution.isInFlight)
    }

    @Test
    fun `an open pull request is in flight, not accepted`() {
        authored(pullRequest())

        val contribution = service.forHire(member(), projectId).single()

        assertEquals(ContributionState.IN_FLIGHT, contribution.state)
        assertEquals(null, contribution.acceptedAt)
        assertTrue(contribution.isInFlight)
    }

    @Test
    fun `a pull request closed without merging is abandoned, not in flight`() {
        authored(pullRequest(state = "CLOSED"))

        val contribution = service.forHire(member(), projectId).single()

        // Waiting on nobody: counting this as in flight would inflate every "waiting for a
        // response" number on the PM dashboard.
        assertEquals(ContributionState.ABANDONED, contribution.state)
        assertFalse(contribution.isInFlight)
        assertFalse(contribution.isAccepted)
    }

    @Test
    fun `a merged pull request is observed evidence, not attested`() {
        authored(pullRequest(merged = daysAgo(3), state = "MERGED"))

        val contribution = service.forHire(member(), projectId).single()

        assertEquals(Rigor.OBSERVED, contribution.rigor)
        assertEquals(ContributionEvidenceKind.PULL_REQUEST, contribution.kind)
    }

    @Test
    fun `review rework carries through as the returned count`() {
        authored(pullRequest(merged = daysAgo(3), state = "MERGED", changesRequested = 2))

        assertEquals(2, service.forHire(member(), projectId).single().returnedCount)
    }

    @Test
    fun `a hire with no declared identity has no contributions and is never looked up`() {
        val contributions = service.forHire(member(login = null), projectId)

        assertEquals(emptyList(), contributions)
        // Not "did no work": there is nothing to attribute work to, so no lookup is even possible.
        verify(exactly = 0) { artifactIngestionApi.getAuthoredPullRequests(any(), any()) }
    }

    @Test
    fun `a blank identity is treated the same as an absent one`() {
        assertEquals(emptyList(), service.forHire(member(login = "   "), projectId))
    }
}
