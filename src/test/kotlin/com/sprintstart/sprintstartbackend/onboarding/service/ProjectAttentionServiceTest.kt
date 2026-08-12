package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.AttentionSeverity
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireVocabularyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.ProjectOnboardingMetricsResponse
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ProjectAttentionServiceTest {
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val onboardingMetricsService: OnboardingMetricsService = mockk()

    private val now: Instant = Instant.parse("2026-07-22T12:00:00Z")
    private val projectId: UUID = UUID.randomUUID()

    private val service = ProjectAttentionService(
        projectMembershipApi,
        onboardingMetricsService,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun member(userId: UUID, name: String = "A Hire") =
        ProjectMember(userId = userId, displayName = name, githubLogin = "hire", joinedAt = null)

    private fun timeline(
        userId: UUID,
        longestOpenWaitHours: Long? = null,
        stalled: Boolean = false,
        stalledReason: String? = null,
    ) = HireTimelineResponse(
        userId = userId,
        displayName = "A Hire",
        githubLogin = "hire",
        joinedAt = null,
        taskZeroAssignedAt = null,
        firstTaskClaimedAt = null,
        firstContributionOpenedAt = null,
        firstResponseAt = null,
        firstContributionAcceptedAt = null,
        hoursToFirstAcceptedContribution = null,
        hoursToFirstResponse = null,
        acceptedContributionCount = 0,
        openContributionCount = 0,
        longestOpenWaitHours = longestOpenWaitHours,
        stalled = stalled,
        stalledReason = stalledReason,
        autonomyReachedAt = null,
        vocabulary = HireVocabularyResponse(
            trackLabel = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
        ),
        returnedContributionCount = 0,
    )

    private fun projectWith(vararg hires: HireTimelineResponse) {
        every { projectMembershipApi.getProjectMembers(projectId) } returns
            hires.map { member(it.userId) }
        every { onboardingMetricsService.getProjectMetrics(projectId) } returns
            ProjectOnboardingMetricsResponse(
                projectId = projectId,
                memberCount = hires.size,
                unattributableMemberCount = 0,
                hiresWithAcceptedContribution = 0,
                medianHoursToFirstAcceptedContribution = null,
                medianHoursToFirstResponse = null,
                p90HoursToFirstResponse = null,
                stalledCount = hires.count { it.stalled },
                waitingOnResponseCount = hires.count { it.longestOpenWaitHours != null },
                hires = hires.toList(),
            )
    }

    @Test
    fun `a pull request waiting past the window is blocked, and somebody else's move`() {
        val hire = UUID.randomUUID()
        projectWith(timeline(hire, longestOpenWaitHours = 96))

        val items = service.getAttention(projectId).items

        assertThat(items).hasSize(1)
        assertThat(items.single().severity).isEqualTo(AttentionSeverity.BLOCKED)
        assertThat(items.single().reason).contains("waiting 4 days for a response")
        assertThat(items.single().days).isEqualTo(4)
    }

    @Test
    fun `a wait inside the window is not an attention item`() {
        projectWith(timeline(UUID.randomUUID(), longestOpenWaitHours = 24))

        assertThat(service.getAttention(projectId).items).isEmpty()
    }

    @Test
    fun `a stall the waiting row does not already cover drifts`() {
        projectWith(
            timeline(UUID.randomUUID(), stalled = true, stalledReason = "No attributable GitHub login"),
        )

        val items = service.getAttention(projectId).items

        assertThat(items).hasSize(1)
        assertThat(items.single().severity).isEqualTo(AttentionSeverity.DRIFTING)
        assertThat(items.single().reason).contains("GitHub login")
    }

    @Test
    fun `a stalled hire with a waiting pull request surfaces only the wait`() {
        projectWith(
            timeline(UUID.randomUUID(), longestOpenWaitHours = 72, stalled = true, stalledReason = "Silence"),
        )

        val items = service.getAttention(projectId).items

        // One root cause, one item: the wait already says what needs doing.
        assertThat(items).hasSize(1)
        assertThat(items.single().severity).isEqualTo(AttentionSeverity.BLOCKED)
    }

    @Test
    fun `blocked sorts before drifting`() {
        val waiting = UUID.randomUUID()
        val stalled = UUID.randomUUID()
        projectWith(
            timeline(stalled, stalled = true, stalledReason = "No activity"),
            timeline(waiting, longestOpenWaitHours = 96),
        )

        val items = service.getAttention(projectId).items

        assertThat(items.map { it.severity }).containsExactly(
            AttentionSeverity.BLOCKED,
            AttentionSeverity.DRIFTING,
        )
    }
}
