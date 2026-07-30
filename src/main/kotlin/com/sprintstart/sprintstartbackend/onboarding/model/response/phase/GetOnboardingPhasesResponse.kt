package com.sprintstart.sprintstartbackend.onboarding.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.model.response.check.PhaseCheckSummaryResponse
import java.util.UUID

data class GetOnboardingPhasesResponse(
    val id: UUID,
    val pathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    // Mirrors the user-facing path response so reviewer screens can show whether the
    // phase has a knowledge check and how the user did on it.
    val checkSummary: PhaseCheckSummaryResponse,
)
