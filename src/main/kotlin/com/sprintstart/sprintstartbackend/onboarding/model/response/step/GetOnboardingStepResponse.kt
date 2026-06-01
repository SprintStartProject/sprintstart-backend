package com.sprintstart.sprintstartbackend.onboarding.model.response.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import java.time.Instant
import java.util.UUID

data class GetOnboardingStepResponse(
    val id: UUID,
    val phaseId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val estimatedMinutes: Int,
    val tasks: List<GetOnboardingTasksResponse>,
    val resources: List<GetOnboardingResourcesResponse>,
    val status: StepStatus,
    val completedAt: Instant?,
    val skipReason: String?,
)
