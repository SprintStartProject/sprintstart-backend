package com.sprintstart.sprintstartbackend.onboarding.model.response.path

import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import java.util.UUID

data class OnboardingGenerationIssueResponse(
    val phaseId: UUID,
    val title: String,
    val status: GenerationStatus,
)
