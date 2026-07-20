package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.PhaseUnlockReason
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.CreateOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathsResponse

fun OnboardingPath.toGetAllResponse(): GetOnboardingPathsResponse {
    return GetOnboardingPathsResponse(
        id = this.id,
        userId = this.userId,
        createdAt = this.createdAt,
        phaseCount = phases.count(),
        stepCount = phases.sumOf { phase -> phase.steps.size },
        finishedStepCount = phases.sumOf { phase ->
            phase.steps.count { step -> step.status == StepStatus.FINISHED || step.status == StepStatus.SKIPPED }
        },
    )
}

fun OnboardingPath.toGetResponse(): GetOnboardingPathResponse {
    return GetOnboardingPathResponse(
        id = this.id,
        userId = this.userId,
        createdAt = this.createdAt,
        phases = phases.map { phase -> phase.toGetAllResponse() },
    )
}

fun OnboardingPath.toGetForUserResponse(): GetOnboardingPathForUserResponse {
    // The reason the phase being visited is locked; null while every previous
    // phase is completed (steps done/skipped and check passed if one exists).
    var lockReason: PhaseUnlockReason? = null

    val phaseResponses = phases.sortedBy { it.position }.map { phase ->
        val response = phase.toGetForUserResponse(
            locked = lockReason != null,
            unlockReason = lockReason,
        )

        // A locked phase locks everything after it as well.
        lockReason = when {
            lockReason != null || !phase.stepsCompleted() -> PhaseUnlockReason.PREVIOUS_PHASE_INCOMPLETE
            phase.hasCheck() && !phase.checkPassed() -> PhaseUnlockReason.PREVIOUS_PHASE_CHECK_NOT_PASSED
            else -> null
        }

        response
    }

    return GetOnboardingPathForUserResponse(
        id = this.id,
        userId = this.userId,
        createdAt = this.createdAt,
        phases = phaseResponses,
    )
}

fun OnboardingPath.toCreateResponse(): CreateOnboardingPathResponse {
    return CreateOnboardingPathResponse(
        id = this.id,
        userId = this.userId,
        createdAt = this.createdAt,
    )
}
