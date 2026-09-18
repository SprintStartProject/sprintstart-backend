package com.sprintstart.sprintstartbackend.onboarding.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import java.util.UUID

data class GetOnboardingPhaseForUserResponse(
    val id: UUID,
    val pathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val locked: Boolean,
    val steps: List<GetOnboardingStepsResponse>,
    val questions: List<GetOnboardingQuestionForUserResponse> = emptyList(),
    val graphX: Double? = null,
    val graphY: Double? = null,
    val blockerIds: Set<UUID> = emptySet(),
    val generationStatus: GenerationStatus = GenerationStatus.NOT_APPLICABLE,
)
