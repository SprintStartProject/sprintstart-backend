package com.sprintstart.sprintstartbackend.onboarding.blueprint.factory

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhaseRequirement
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Creates deep copies of blueprint paths, including their whole content tree.
 *
 * The copy covers phases, requirements, steps, tasks, resources, check questions,
 * and check options. Dependency edges (`blockedBy`) between phases and between
 * steps/questions are not copied directly; instead, the factory records every
 * created entity in old-ID to new-entity maps and rewires the edges in a second
 * pass, so the copied graph references only the new entities.
 */
@Component
class BlueprintPathCopyFactory {
    /**
     * Copies [path] into a new blueprint path with the given identity and lifecycle values.
     *
     * The copy is detached from persistence (no IDs are carried over) and must be
     * saved by the caller. `blockedBy` edges are preserved: phases keep their phase
     * blockers and steps/check questions keep their node blockers, remapped onto the
     * copied entities.
     *
     * @param path the blueprint path to copy.
     * @param blueprintKey the blueprint key assigned to the copy.
     * @param projectId the project the copy belongs to, or `null` for a global blueprint.
     * @param status the lifecycle status the copy starts with.
     * @param version the version number assigned to the copy.
     * @return the new, unpersisted blueprint path with its full content tree.
     */
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
        val newNodesByOldId = mutableMapOf<UUID, BlueprintSubGraphNode>()
        val newPhasesByOldId = mutableMapOf<UUID, BlueprintPhase>()

        path.blueprintPhases
            .map { copyPhase(it, copy, newPhasesByOldId, newNodesByOldId) }
            .forEach(copy.blueprintPhases::add)

        path.blueprintPhases
            .forEach { oldPhase ->
                val newPhase = newPhasesByOldId.getValue(oldPhase.id)

                oldPhase.blockedBy.forEach { oldBlocker ->
                    newPhase.blockedBy.add(newPhasesByOldId.getValue(oldBlocker.id))
                }
            }

        path.blueprintPhases
            .flatMap { it.blueprintSteps + it.blueprintCheckQuestions }
            .forEach { oldNode ->
                val newNode = newNodesByOldId.getValue(oldNode.id)

                oldNode.blockedBy.forEach { oldBlocker ->
                    newNode.blockedBy.add(newNodesByOldId.getValue(oldBlocker.id))
                }
            }

        return copy
    }

    private fun copyPhase(
        phase: BlueprintPhase,
        newPath: BlueprintPath,
        newPhasesByOldId: MutableMap<UUID, BlueprintPhase>,
        newNodesByOldId: MutableMap<UUID, BlueprintSubGraphNode>,
    ): BlueprintPhase {
        val newPhase = BlueprintPhase(
            blueprintPath = newPath,
            position = phase.position,
            title = phase.title,
            description = phase.description,
            aiPrompt = phase.aiPrompt,
            type = phase.type,
            graphX = phase.graphX,
            graphY = phase.graphY,
        )

        newPhasesByOldId[phase.id] = newPhase

        phase.requirements
            .map { copyRequirement(it, newPhase) }
            .forEach(newPhase.requirements::add)

        phase.blueprintSteps
            .map { copyStep(it, newPhase, newNodesByOldId) }
            .forEach(newPhase.blueprintSteps::add)

        phase.blueprintCheckQuestions
            .map { copyQuestion(it, newPhase, newNodesByOldId) }
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
        newNodesByOldId: MutableMap<UUID, BlueprintSubGraphNode>,
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
            graphX = step.graphX,
            graphY = step.graphY,
        )

        newNodesByOldId[step.id] = newStep

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
        newNodesByOldId: MutableMap<UUID, BlueprintSubGraphNode>,
    ): BlueprintCheckQuestion {
        val newQuestion = BlueprintCheckQuestion(
            blueprintPhase = newPhase,
            title = question.title,
            position = question.position,
            type = question.type,
            question = question.question,
            explanation = question.explanation,
            correctAnswer = question.correctAnswer,
            graphX = question.graphX,
            graphY = question.graphY,
        )

        newNodesByOldId[question.id] = newQuestion

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
