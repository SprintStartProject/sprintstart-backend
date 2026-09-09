package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.PhaseUnlockReason
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse

fun OnboardingPhase.toGetAllResponse(): GetOnboardingPhasesResponse {
    return GetOnboardingPhasesResponse(
        id = this.id,
        pathId = this.path.id,
        position = this.position,
        title = this.title,
        description = this.description,
        checkSummary = toCheckSummaryResponse(),
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun OnboardingPhase.toGetResponse(): GetOnboardingPhaseResponse {
    return GetOnboardingPhaseResponse(
        id = this.id,
        pathId = this.path.id,
        position = this.position,
        title = this.title,
        description = this.description,
        steps = this.steps.map { step -> step.toGetAllResponse() },
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun OnboardingPhase.toGetForUserResponse(
    locked: Boolean = false,
    unlockReason: PhaseUnlockReason? = null,
): GetOnboardingPhaseForUserResponse {
    return GetOnboardingPhaseForUserResponse(
        id = this.id,
        pathId = this.path.id,
        position = this.position,
        title = this.title,
        description = this.description,
        locked = locked,
        unlockReason = unlockReason,
        checkSummary = this.toCheckSummaryResponse(),
        steps = this.steps.map { step -> step.toGetAllResponse() },
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun OnboardingPhase.toCreateResponse(): CreateOnboardingPhaseResponse {
    return CreateOnboardingPhaseResponse(
        id = this.id,
        pathId = this.path.id,
        position = this.position,
        title = this.title,
        description = this.description,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun OnboardingPhase.toUpdateResponse(): UpdateOnboardingPhaseResponse {
    return UpdateOnboardingPhaseResponse(
        id = this.id,
        pathId = this.path.id,
        position = this.position,
        title = this.title,
        description = this.description,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}
