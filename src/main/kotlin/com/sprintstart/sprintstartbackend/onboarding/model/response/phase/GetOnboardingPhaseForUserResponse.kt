package com.sprintstart.sprintstartbackend.onboarding.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.external.enums.PhaseUnlockReason
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.PhaseCheckSummaryResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import java.util.UUID

data class GetOnboardingPhaseForUserResponse(
    val id: UUID,
    val pathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val locked: Boolean,
    val unlockReason: PhaseUnlockReason?,
    val checkSummary: PhaseCheckSummaryResponse,
    val steps: List<GetOnboardingStepsResponse>,
)
