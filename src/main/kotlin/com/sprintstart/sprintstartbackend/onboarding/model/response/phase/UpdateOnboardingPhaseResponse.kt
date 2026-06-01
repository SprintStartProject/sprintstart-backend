package com.sprintstart.sprintstartbackend.onboarding.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import java.util.UUID

data class UpdateOnboardingPhaseResponse(
    val id: UUID,
    val pathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
)
