package com.sprintstart.sprintstartbackend.path.model.response.task

import java.util.UUID

data class GetOnboardingTaskResponse(
    val id: UUID,
    val stepId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val finished: Boolean,
)
