package com.sprintstart.sprintstartbackend.onboarding.blueprint.factory

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class BlueprintPathCopyFactoryTest {
    @Test
    fun `copies phase and mixed subgraph dependencies`() {
        val source = BlueprintPath(blueprintKey = UUID.randomUUID(), title = "Source")
        val first = phase(source, 0, "First", 1.0, 2.0)
        val second = phase(source, 1, "Second", 3.0, 4.0)
        second.blockedBy += first
        source.blueprintPhases += listOf(first, second)

        val step = BlueprintStep(
            blueprintPhase = first,
            title = "Read",
            position = 0,
            description = "Read docs",
            type = StepType.DOCUMENT,
            estimatedMinutes = 10,
            expectedOutcome = "Understood",
            graphX = 5.0,
            graphY = 6.0,
        )
        val question = BlueprintCheckQuestion(
            blueprintPhase = first,
            title = "Check",
            position = 1,
            type = CheckQuestionType.SHORT_TEXT,
            question = "What did you learn?",
            correctAnswer = "The architecture",
            graphX = 7.0,
            graphY = 8.0,
        )
        question.blockedBy += step
        first.blueprintSteps += step
        first.blueprintCheckQuestions += question

        val copy = BlueprintPathCopyFactory().createCopyFrom(
            path = source,
            blueprintKey = source.blueprintKey,
            projectId = UUID.randomUUID(),
            status = BlueprintStatus.DRAFT,
            version = 1,
        )

        val copiedFirst = copy.blueprintPhases[0]
        val copiedSecond = copy.blueprintPhases[1]
        val copiedStep = copiedFirst.blueprintSteps.single()
        val copiedQuestion = copiedFirst.blueprintCheckQuestions.single()
        assertEquals(1.0, copiedFirst.graphX)
        assertEquals(2.0, copiedFirst.graphY)
        assertEquals(setOf(copiedFirst.id), copiedSecond.blockedBy.map { it.id }.toSet())
        assertEquals(5.0, copiedStep.graphX)
        assertEquals(8.0, copiedQuestion.graphY)
        assertEquals(setOf(copiedStep.id), copiedQuestion.blockedBy.map { it.id }.toSet())
    }

    private fun phase(
        path: BlueprintPath,
        position: Int,
        title: String,
        graphX: Double,
        graphY: Double,
    ): BlueprintPhase {
        return BlueprintPhase(
            blueprintPath = path,
            position = position,
            title = title,
            description = null,
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
            graphX = graphX,
            graphY = graphY,
        )
    }
}
