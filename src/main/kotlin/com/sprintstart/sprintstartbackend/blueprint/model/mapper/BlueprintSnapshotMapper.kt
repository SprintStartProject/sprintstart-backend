package com.sprintstart.sprintstartbackend.blueprint.model.mapper

import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintCheckOptionSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintCheckQuestionSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintPhaseSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintRequirementSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintResourceSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintStepSnapshot
import com.sprintstart.sprintstartbackend.blueprint.external.BlueprintTaskSnapshot
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintStep

/** Copies the blueprint and its whole graph into a [BlueprintSnapshot]; reads every lazy relation. */
fun BlueprintPath.toSnapshot(): BlueprintSnapshot =
    BlueprintSnapshot(
        id = id,
        projectId = projectId,
        phases = blueprintPhases.map { it.toSnapshot() },
    )

private fun BlueprintPhase.toSnapshot() =
    BlueprintPhaseSnapshot(
        id = id,
        position = position,
        title = title,
        description = description,
        aiPrompt = aiPrompt,
        type = type,
        graphX = graphX,
        graphY = graphY,
        blockedBy = blockedBy.map { it.id }.toSet(),
        requirements = requirements.map { BlueprintRequirementSnapshot(it.type, it.referenceId) },
        steps = blueprintSteps.map { it.toSnapshot() },
        checkQuestions = blueprintCheckQuestions.map { it.toSnapshot() },
    )

private fun BlueprintStep.toSnapshot() =
    BlueprintStepSnapshot(
        id = id,
        position = position,
        title = title,
        description = description,
        type = type,
        aiAssisted = aiAssisted,
        estimatedMinutes = estimatedMinutes,
        expectedOutcome = expectedOutcome,
        graphX = graphX,
        graphY = graphY,
        blockedBy = blockedBy.map { it.id }.toSet(),
        tasks = blueprintTasks.map { BlueprintTaskSnapshot(it.position, it.title, it.description) },
        resources = blueprintResources.map { BlueprintResourceSnapshot(it.title, it.description, it.url) },
    )

private fun BlueprintCheckQuestion.toSnapshot() =
    BlueprintCheckQuestionSnapshot(
        id = id,
        title = title,
        position = position,
        type = type,
        question = question,
        explanation = explanation,
        correctAnswer = correctAnswer,
        graphX = graphX,
        graphY = graphY,
        blockedBy = blockedBy.map { it.id }.toSet(),
        options = blueprintCheckOptions.map { BlueprintCheckOptionSnapshot(it.position, it.label, it.correct) },
    )
