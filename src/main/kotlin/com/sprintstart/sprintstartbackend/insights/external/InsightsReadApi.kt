package com.sprintstart.sprintstartbackend.insights.external

import com.sprintstart.sprintstartbackend.insights.model.dto.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.metrics.ProjectAttentionResponse
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

/** The onboarding metrics the assistants read: one hire's timeline, and who on a project needs attention. */
interface InsightsReadApi {
    /** [hireId]'s onboarding timeline on [projectId], or null when they are not a member there. */
    fun hireTimeline(hireId: UserId, projectId: ProjectId): HireTimelineResponse?

    /** Who on [projectId] needs attention: blocked before drifting, longest-waiting first. */
    fun attentionOn(projectId: ProjectId): ProjectAttentionResponse
}
