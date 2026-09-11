package com.sprintstart.sprintstartbackend.onboarding.model.response.phase

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import java.util.UUID

data class GetOnboardingPhasesResponse(
    val id: UUID,
    val pathId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val graphX: Double? = null,
    val graphY: Double? = null,
    val blockerIds: Set<UUID> = emptySet(),
    val generationStatus: GenerationStatus = GenerationStatus.NOT_APPLICABLE,
)
