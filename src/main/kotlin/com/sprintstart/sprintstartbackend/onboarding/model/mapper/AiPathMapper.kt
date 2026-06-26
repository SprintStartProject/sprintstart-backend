package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.external.model.PathPhase
import com.sprintstart.sprintstartbackend.onboarding.external.model.PathResource
import com.sprintstart.sprintstartbackend.onboarding.external.model.PathStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import java.util.UUID

fun OnboardingPath.toEntities(userId: UUID): com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath {
    val path = com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath(
        userId = userId,
    )
    path.phases.addAll(
        phases.mapIndexed { index, phase -> phase.toEntity(path, index) },
    )
    return path
}

private fun PathPhase.toEntity(
    path: com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath,
    index: Int,
): OnboardingPhase {
    val phase = OnboardingPhase(
        path = path,
        position = this.position.takeIf { it != 0 } ?: index,
        title = this.title,
        description = this.description ?: "",
    )
    phase.steps.addAll(
        steps.mapIndexed { stepIndex, step -> step.toEntity(phase, stepIndex) },
    )
    return phase
}

private fun PathStep.toEntity(phase: OnboardingPhase, index: Int): OnboardingStep {
    val step = OnboardingStep(
        phase = phase,
        position = index,
        title = this.title,
        description = this.description ?: "",
        type = StepType.TASK,
        estimatedMinutes = 0,
        expectedOutcome = this.description ?: "",
        status = StepStatus.WAITING,
    )
    step.resources.addAll(
        resources.map { it.toEntity(step) },
    )
    step.tasks.addAll(
        tasks.mapIndexed { taskIndex, task ->
            OnboardingTask(
                step = step,
                position = taskIndex,
                title = task.title,
                description = task.description ?: "",
            )
        },
    )
    return step
}

private fun PathResource.toEntity(step: OnboardingStep): OnboardingResource =
    OnboardingResource(
        step = step,
        title = this.filename ?: "",
        description = "",
        url = this.note ?: "",
    )
