package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.CreateOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingGenerationIssueResponse
import java.util.UUID

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
        blueprintId = this.blueprintId,
    )
}

fun OnboardingPath.toGetResponse(): GetOnboardingPathResponse {
    return GetOnboardingPathResponse(
        id = this.id,
        userId = this.userId,
        createdAt = this.createdAt,
        phases = phases.map { phase -> phase.toGetAllResponse() },
        blueprintId = this.blueprintId,
    )
}

/**
 * Maps the path for its owner, deriving per-phase and per-node lock state from the real
 * blocker graph (see [OnboardingAvailability]).
 *
 * Phases whose [com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus]
 * is hidden from users are left out of `phases` and reported in `generationIssues` instead;
 * the lock state is computed over the visible phases only.
 *
 * @param passedQuestionIds IDs of questions the user has answered correctly at least once.
 * @param attemptedQuestionIds IDs of questions the user has attempted (correctly or not).
 */
fun OnboardingPath.toGetForUserResponse(
    passedQuestionIds: Set<UUID> = emptySet(),
    attemptedQuestionIds: Set<UUID> = emptySet(),
): GetOnboardingPathForUserResponse {
    val sortedPhases = phases.sortedBy { it.position }
    val visiblePhases = sortedPhases.filterNot { it.generationStatus.isHiddenFromUser() }
    val phaseLocked = computePhaseLockedState(visiblePhases, passedQuestionIds)

    return GetOnboardingPathForUserResponse(
        id = this.id,
        userId = this.userId,
        createdAt = this.createdAt,
        phases = visiblePhases.map { phase ->
            val locked = phaseLocked[phase.id] ?: false
            phase.toGetForUserResponse(
                locked = locked,
                passedQuestionIds = passedQuestionIds,
                attemptedQuestionIds = attemptedQuestionIds,
            )
        },
        blueprintId = this.blueprintId,
        generationIssues = sortedPhases
            .filter { it.generationStatus.isHiddenFromUser() }
            .map { phase ->
                OnboardingGenerationIssueResponse(
                    phaseId = phase.id,
                    title = phase.title,
                    status = phase.generationStatus,
                )
            },
    )
}

fun OnboardingPath.toCreateResponse(): CreateOnboardingPathResponse {
    return CreateOnboardingPathResponse(
        id = this.id,
        userId = this.userId,
        createdAt = this.createdAt,
        blueprintId = this.blueprintId,
    )
}
