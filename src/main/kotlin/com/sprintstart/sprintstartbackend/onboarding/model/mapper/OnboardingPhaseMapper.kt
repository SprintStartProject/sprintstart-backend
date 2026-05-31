package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.response.GetOnboardingPhaseResponse

fun OnboardingPhase.toGetResponse(): GetOnboardingPhaseResponse {
    return GetOnboardingPhaseResponse(
        id = this.id,
    )
}
