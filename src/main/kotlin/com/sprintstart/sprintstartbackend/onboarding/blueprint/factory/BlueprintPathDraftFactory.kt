package com.sprintstart.sprintstartbackend.onboarding.blueprint.factory

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhaseRequirement
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import org.springframework.stereotype.Component

@Component
class BlueprintPathDraftFactory {
    fun createDraftFrom(path: BlueprintPath): BlueprintPath {
        val draft = BlueprintPath(
            blueprintKey = path.blueprintKey,
            projectId = path.projectId,
            title = path.title,
            description = path.description,
            version = path.version + 1,
            status = BlueprintStatus.DRAFT,
        )

        path.blueprintPhases
            .map { copyPhase(it, draft) }
            .forEach(draft.blueprintPhases::add)

        return draft
    }

    fun copyPhase(
        phase: BlueprintPhase,
        newPath: BlueprintPath,
    ): BlueprintPhase {
        val newPhase = BlueprintPhase(
            blueprintPath = newPath,
            position = phase.position,
            title = phase.title,
            description = phase.description,
            aiPrompt = phase.aiPrompt,
            type = phase.type,
        )

        phase.requirements
            .map { copyRequirement(it, newPhase) }
            .forEach(newPhase.requirements::add)

        phase.blueprintSteps
            .map { copyStep(it, newPhase) }
            .forEach(newPhase.blueprintSteps::add)

        phase.blueprintCheckQuestions
            .map { copyQuestion(it, newPhase) }
            .forEach(newPhase.blueprintCheckQuestions::add)

        return newPhase
    }

    fun copyRequirement(
        requirement: BlueprintPhaseRequirement,
        newPhase: BlueprintPhase,
    ): BlueprintPhaseRequirement {
        return BlueprintPhaseRequirement(
            blueprintPhase = newPhase,
            type = requirement.type,
            referenceId = requirement.referenceId,
            displayName = requirement.displayName,
        )
    }

    fun copyStep(
        step: BlueprintStep,
        newPhase: BlueprintPhase,
    ): BlueprintStep {
        val newStep = BlueprintStep(
            blueprintPhase = newPhase,
            position = step.position,
            title = step.title,
            description = step.description,
            type = step.type,
            aiAssisted = step.aiAssisted,
            estimatedMinutes = step.estimatedMinutes,
            expectedOutcome = step.expectedOutcome,
        )

        step.blueprintTasks
            .map { copyTask(it, newStep) }
            .forEach(newStep.blueprintTasks::add)

        step.blueprintResources
            .map { copyResource(it, newStep) }
            .forEach(newStep.blueprintResources::add)

        return newStep
    }

    fun copyTask(
        task: BlueprintTask,
        newStep: BlueprintStep,
    ): BlueprintTask {
        return BlueprintTask(
            blueprintStep = newStep,
            position = task.position,
            title = task.title,
            description = task.description,
        )
    }

    fun copyResource(
        resource: BlueprintResource,
        newStep: BlueprintStep,
    ): BlueprintResource {
        return BlueprintResource(
            blueprintStep = newStep,
            title = resource.title,
            description = resource.description,
            url = resource.url,
        )
    }

    fun copyQuestion(
        question: BlueprintCheckQuestion,
        newPhase: BlueprintPhase,
    ): BlueprintCheckQuestion {
        val newQuestion = BlueprintCheckQuestion(
            blueprintPhase = newPhase,
            position = question.position,
            type = question.type,
            question = question.question,
            explanation = question.explanation,
            correctAnswer = question.correctAnswer,
        )

        question.blueprintCheckOptions
            .map { copyOption(it, newQuestion) }
            .forEach(newQuestion.blueprintCheckOptions::add)

        return newQuestion
    }

    fun copyOption(
        option: BlueprintCheckOption,
        newQuestion: BlueprintCheckQuestion,
    ): BlueprintCheckOption {
        return BlueprintCheckOption(
            blueprintCheckQuestion = newQuestion,
            position = option.position,
            label = option.label,
            correct = option.correct,
        )
    }
}
