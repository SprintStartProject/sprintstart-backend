package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertSame

/** The insights facade only unwraps ids and hands each read to the service that owns it. */
class InsightsReadServiceTest {
    private val onboardingMetricsService: OnboardingMetricsService = mockk()
    private val projectAttentionService: ProjectAttentionService = mockk()
    private val reads = InsightsReadService(onboardingMetricsService, projectAttentionService)

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    @Test
    fun `reads answer with what the owning service answers`() {
        every { onboardingMetricsService.getHireTimeline(userId, projectId) } returns mockk()
        every { projectAttentionService.getAttention(projectId) } returns mockk()

        assertSame(
            onboardingMetricsService.getHireTimeline(userId, projectId),
            reads.hireTimeline(UserId(userId), ProjectId(projectId)),
        )
        assertSame(projectAttentionService.getAttention(projectId), reads.attentionOn(ProjectId(projectId)))
    }
}
