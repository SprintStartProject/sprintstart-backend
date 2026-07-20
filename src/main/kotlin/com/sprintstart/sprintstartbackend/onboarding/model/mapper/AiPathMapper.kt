package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.external.model.PathPhase
import com.sprintstart.sprintstartbackend.onboarding.external.model.PathResource
import com.sprintstart.sprintstartbackend.onboarding.external.model.PathStep
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseCheck
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckOption
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
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
    phase.checkQuestions.addAll(check.toQuestionEntities(phase))
    return phase
}

/**
 * Maps the generated knowledge check into question entities, best-effort.
 *
 * Invalid questions are skipped rather than failing path assembly, mirroring the
 * AI service's own "degrade to an empty check" behavior: a multiple choice needs
 * at least two options and one correct one, a short text needs a reference answer,
 * and unknown question types are dropped.
 */
private fun PhaseCheck.toQuestionEntities(phase: OnboardingPhase): List<PhaseCheckQuestion> =
    questions.mapNotNull { it.toQuestionEntityOrNull(phase) }

private fun AiCheckQuestion.toQuestionEntityOrNull(phase: OnboardingPhase): PhaseCheckQuestion? {
    if (question.isBlank()) return null

    val questionType = when (type) {
        "MULTIPLE_CHOICE" -> CheckQuestionType.MULTIPLE_CHOICE
        "SHORT_TEXT" -> CheckQuestionType.SHORT_TEXT
        else -> return null
    }

    when (questionType) {
        CheckQuestionType.MULTIPLE_CHOICE -> if (options.size < 2 || options.none { it.correct }) return null
        CheckQuestionType.SHORT_TEXT -> if (correctAnswer.isNullOrBlank()) return null
    }

    val entity = PhaseCheckQuestion(
        phase = phase,
        position = position,
        type = questionType,
        question = question,
        explanation = explanation,
        correctAnswer = correctAnswer.takeIf { questionType == CheckQuestionType.SHORT_TEXT },
    )
    if (questionType == CheckQuestionType.MULTIPLE_CHOICE) {
        options.forEach { option ->
            entity.options += PhaseCheckOption(
                question = entity,
                position = option.position,
                label = option.label,
                correct = option.correct,
            )
        }
    }
    return entity
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
