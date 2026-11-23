package com.sprintstart.sprintstartbackend.path.model.response.path

import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import java.time.Instant
import java.util.UUID

data class GetOnboardingPathForUserResponse(
    val id: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val phases: List<GetOnboardingPhaseForUserResponse>,
    val blueprintId: UUID? = null,
    val generationIssues: List<OnboardingGenerationIssueResponse> = emptyList(),
)
