package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse

fun OnboardingStep.toGetAllResponse(): GetOnboardingStepsResponse {
    return GetOnboardingStepsResponse(
        id = this.id,
        phaseId = this.phase.id,
        position = this.position,
        title = this.title,
        description = this.description,
        type = this.type,
        estimatedMinutes = this.estimatedMinutes,
        isAiAssisted = this.aiAssisted,
        expectedOutcomes = listOf(this.expectedOutcome),
        status = this.status,
        startedAt = this.startedAt,
        completedAt = this.completedAt,
        feedback = this.feedback.lastOrNull()?.toGetResponse(),
        skip = this.skips.lastOrNull()?.toStepResponse(),
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun OnboardingStep.toGetResponse(): GetOnboardingStepResponse {
    return GetOnboardingStepResponse(
        id = this.id,
        phaseId = this.phase.id,
        position = this.position,
        title = this.title,
        description = this.description,
        estimatedMinutes = this.estimatedMinutes,
        type = this.type,
        isAiAssisted = this.aiAssisted,
        expectedOutcomes = listOf(this.expectedOutcome),
        tasks = this.tasks.map { task -> task.toGetAllResponse() },
        resources = this.resources.map { resource -> resource.toGetAllResponse() },
        status = this.status,
        startedAt = this.startedAt,
        completedAt = this.completedAt,
        feedback = this.feedback.lastOrNull()?.toGetResponse(),
        skip = this.skips.lastOrNull()?.toStepResponse(),
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun OnboardingStep.toCreateResponse(): CreateOnboardingStepResponse {
    return CreateOnboardingStepResponse(
        id = this.id,
        phaseId = this.phase.id,
        position = this.position,
        title = this.title,
        description = this.description,
        type = this.type,
        estimatedMinutes = this.estimatedMinutes,
        isAiAssisted = this.aiAssisted,
        expectedOutcome = this.expectedOutcome,
        status = this.status,
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}

fun OnboardingStep.toUpdateResponse(): UpdateOnboardingStepResponse {
    return UpdateOnboardingStepResponse(
        id = this.id,
        phaseId = this.phase.id,
        position = this.position,
        title = this.title,
        description = this.description,
        estimatedMinutes = this.estimatedMinutes,
        isAiAssisted = this.aiAssisted,
        expectedOutcome = this.expectedOutcome,
        status = this.status,
        startedAt = this.startedAt,
        completedAt = this.completedAt,
        feedback = this.feedback.lastOrNull()?.toGetResponse(),
        skip = this.skips.lastOrNull()?.toStepResponse(),
        graphX = this.graphX,
        graphY = this.graphY,
        blockerIds = this.blockedBy.map { it.id }.toSet(),
    )
}
