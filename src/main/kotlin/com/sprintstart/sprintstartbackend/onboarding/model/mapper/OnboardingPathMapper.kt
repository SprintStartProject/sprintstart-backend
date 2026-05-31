package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.response.GetAllOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.GetOnboardingPathResponse

fun OnboardingPath.toGetAllResponse(): GetAllOnboardingPathResponse {
    return GetAllOnboardingPathResponse(
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
        phases = phases.map { phase -> phase.toGetResponse() },
    )
}
