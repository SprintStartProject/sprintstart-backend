package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.external.InsightsReadApi
import com.sprintstart.sprintstartbackend.insights.model.dto.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.metrics.ProjectAttentionResponse
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Service

/** [InsightsReadApi] over the metrics and attention services. */
@Service
class InsightsReadService(
    private val onboardingMetricsService: OnboardingMetricsService,
    private val projectAttentionService: ProjectAttentionService,
) : InsightsReadApi {
    override fun hireTimeline(hireId: UserId, projectId: ProjectId): HireTimelineResponse? =
        onboardingMetricsService.getHireTimeline(hireId.value, projectId.value)

    override fun attentionOn(projectId: ProjectId): ProjectAttentionResponse =
        projectAttentionService.getAttention(projectId.value)
}
