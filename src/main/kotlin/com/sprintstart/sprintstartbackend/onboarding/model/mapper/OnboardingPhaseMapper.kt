package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse
import java.util.UUID

fun OnboardingPhase.toGetAllResponse(): GetOnboardingPhasesResponse {
    return GetOnboardingPhasesResponse(
        id = this.id,
        pathId = this.path.id,
        position = this.position,
        title = this.title,
        description = this.description,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
        generationStatus = this.generationStatus,
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
        generationStatus = this.generationStatus,
    )
}

/**
 * Maps the phase for the path's owner, resolving each step's `locked` flag and each
 * question's status from the phase lock state and the user's attempt history
 * (see [OnboardingAvailability]).
 *
 * @param locked Whether the phase itself is locked by an incomplete blocker phase.
 * @param passedQuestionIds IDs of questions the user has answered correctly at least once.
 * @param attemptedQuestionIds IDs of questions the user has attempted (correctly or not).
 */
fun OnboardingPhase.toGetForUserResponse(
    locked: Boolean = false,
    passedQuestionIds: Set<UUID> = emptySet(),
    attemptedQuestionIds: Set<UUID> = emptySet(),
): GetOnboardingPhaseForUserResponse {
    return GetOnboardingPhaseForUserResponse(
        id = this.id,
        pathId = this.path.id,
        position = this.position,
        title = this.title,
        description = this.description,
        locked = locked,
        steps = this.steps.sortedBy { it.position }.map { step ->
            step.toGetAllResponse(locked = step.isLockedIn(locked, passedQuestionIds))
        },
        questions = this.checkQuestions.sortedBy { it.position }.map { question ->
            question.toForUserResponse(
                status = question.questionStatus(locked, passedQuestionIds, attemptedQuestionIds),
            )
        },
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
        generationStatus = this.generationStatus,
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
