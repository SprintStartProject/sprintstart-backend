package com.sprintstart.sprintstartbackend.onboarding.model.response.task

import java.util.UUID

data class GetOnboardingTasksResponse(
    val id: UUID,
    val stepId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val finished: Boolean,
)
