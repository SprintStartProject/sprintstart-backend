package com.sprintstart.sprintstartbackend.onboarding.model.response.step

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingStepSkipResponse
import java.time.Instant
import java.util.UUID

data class GetOnboardingStepsResponse(
    val id: UUID,
    val phaseId: UUID,
    val position: Int,
    val title: String,
    val description: String,
    val type: StepType,
    val estimatedMinutes: Int,
    val expectedOutcomes: List<String> = emptyList(),
    val status: StepStatus,
    val startedAt: Instant? = null,
    val completedAt: Instant?,
    val feedback: GetOnboardingFeedbackResponse? = null,
    val skip: GetOnboardingStepSkipResponse?,
)
