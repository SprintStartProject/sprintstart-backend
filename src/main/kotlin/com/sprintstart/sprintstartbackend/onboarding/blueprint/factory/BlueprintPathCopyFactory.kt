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
import java.util.UUID

@Component
class BlueprintPathCopyFactory {
    fun createCopyFrom(
        path: BlueprintPath,
        blueprintKey: UUID,
        projectId: UUID?,
        status: BlueprintStatus,
        version: Int,
    ): BlueprintPath {
        val copy = BlueprintPath(
            blueprintKey = blueprintKey,
            projectId = projectId,
            title = path.title,
            description = path.description,
            version = version,
            status = status,
        )

        path.blueprintPhases
            .map { copyPhase(it, copy) }
            .forEach(copy.blueprintPhases::add)

        return copy
    }

    private fun copyPhase(
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

    private fun copyRequirement(
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

    private fun copyStep(
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

    private fun copyTask(
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

    private fun copyResource(
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

    private fun copyQuestion(
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

    private fun copyOption(
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
