package com.sprintstart.sprintstartbackend.path.model.response.path

import com.sprintstart.sprintstartbackend.path.external.enums.GenerationStatus
import java.util.UUID

data class OnboardingGenerationIssueResponse(
    val phaseId: UUID,
    val title: String,
    val status: GenerationStatus,
    /** What the phase was meant to cover. The buddy talks it through from this to repair the phase. */
    val description: String = "",
)
