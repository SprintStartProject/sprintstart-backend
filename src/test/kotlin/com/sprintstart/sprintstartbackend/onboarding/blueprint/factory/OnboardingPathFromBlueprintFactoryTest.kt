package com.sprintstart.sprintstartbackend.onboarding.blueprint.factory

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhaseRequirement
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class OnboardingPathFromBlueprintFactoryTest {
    private val factory = OnboardingPathFromBlueprintFactory()

    @Test
    fun `creates an independent onboarding aggregate with the complete blueprint graph`() {
        val blueprint = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            title = "Backend onboarding",
        )
        val introduction = phase(blueprint, 0, "Introduction", 10.0, 20.0)
        val delivery = phase(blueprint, 1, "Delivery", 30.0, 40.0)
        delivery.blockedBy += introduction
        blueprint.blueprintPhases += listOf(delivery, introduction)

        val setup = BlueprintStep(
            blueprintPhase = introduction,
            title = "Set up locally",
            position = 0,
            description = "Run the project",
            type = StepType.TASK,
            aiAssisted = false,
            estimatedMinutes = 30,
            expectedOutcome = "Application runs",
            graphX = 5.0,
            graphY = 6.0,
        )
        setup.blueprintTasks += BlueprintTask(
            blueprintStep = setup,
            position = 0,
            title = "Install dependencies",
            description = "Use Gradle",
        )
        setup.blueprintResources += BlueprintResource(
            blueprintStep = setup,
            title = "README",
            description = "Setup guide",
            url = "https://example.test/readme",
        )
        introduction.blueprintSteps += setup

        val check = BlueprintCheckQuestion(
            blueprintPhase = introduction,
            title = "Setup check",
            position = 1,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "Does the application run?",
            graphX = 7.0,
            graphY = 8.0,
        )
        check.blueprintCheckOptions += BlueprintCheckOption(
            blueprintCheckQuestion = check,
            position = 0,
            label = "Yes",
            correct = true,
        )
        check.blockedBy += setup
        introduction.blueprintCheckQuestions += check

        val userId = UUID.randomUUID()
        val result = factory.createFrom(blueprint, userId)

        assertEquals(userId, result.userId)
        assertEquals(blueprint.id, result.blueprintId)
        assertEquals(listOf("Introduction", "Delivery"), result.phases.map { it.title })

        val copiedIntroduction = result.phases[0]
        val copiedDelivery = result.phases[1]
        assertEquals(10.0, copiedIntroduction.graphX)
        assertEquals(20.0, copiedIntroduction.graphY)
        assertEquals(setOf(copiedIntroduction.id), copiedDelivery.blockedBy.map { it.id }.toSet())
        assertNotEquals(introduction.id, copiedIntroduction.id)

        val copiedStep = copiedIntroduction.steps.single()
        val copiedQuestion = copiedIntroduction.checkQuestions.single()
        assertSame(copiedIntroduction, copiedStep.phase)
        assertSame(copiedIntroduction, copiedQuestion.phase)
        assertEquals(StepStatus.WAITING, copiedStep.status)
        assertEquals(5.0, copiedStep.graphX)
        assertEquals(6.0, copiedStep.graphY)
        assertEquals("Install dependencies", copiedStep.tasks.single().title)
        assertEquals("README", copiedStep.resources.single().title)
        assertEquals("Setup check", copiedQuestion.title)
        assertEquals(7.0, copiedQuestion.graphX)
        assertEquals(8.0, copiedQuestion.graphY)
        assertEquals(setOf(copiedStep.id), copiedQuestion.blockedBy.map { it.id }.toSet())
        assertEquals("Yes", copiedQuestion.options.single().label)
    }

    @Test
    fun `copies only phases whose requirements are satisfied`() {
        val blueprint = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            title = "Role-specific onboarding",
        )
        val publicPhase = phase(blueprint, 0, "Everyone", 0.0, 0.0)
        val rolePhase = phase(blueprint, 1, "Backend", 1.0, 1.0)
        val requiredRoleId = UUID.randomUUID()
        rolePhase.requirements += BlueprintPhaseRequirement(
            blueprintPhase = rolePhase,
            type = RequirementType.PROJECT_ROLE,
            referenceId = requiredRoleId,
            displayName = "Backend",
        )
        rolePhase.blockedBy += publicPhase
        blueprint.blueprintPhases += listOf(publicPhase, rolePhase)

        val withoutRole = factory.createFrom(blueprint, UUID.randomUUID())
        val withRole = factory.createFrom(
            blueprintPath = blueprint,
            userId = UUID.randomUUID(),
            projectRoleIds = setOf(requiredRoleId),
        )

        assertEquals(listOf("Everyone"), withoutRole.phases.map { it.title })
        assertEquals(listOf("Everyone", "Backend"), withRole.phases.map { it.title })
        assertEquals(
            setOf(withRole.phases[0].id),
            withRole.phases[1]
                .blockedBy
                .map { it.id }
                .toSet(),
        )
    }

    @Test
    fun `merges generated AI content into an AI enhanced phase`() {
        val blueprint = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            title = "AI onboarding",
        )
        val overview = BlueprintPhase(
            blueprintPath = blueprint,
            position = 0,
            title = "Project Overview",
            description = "Overview",
            aiPrompt = "Generate an overview.",
            type = BlueprintPhaseType.AI_ENHANCED,
        )
        blueprint.blueprintPhases += overview

        val generated = GeneratedPhaseContent(
            steps = listOf(
                GeneratedStep(
                    title = "Read the README",
                    description = "Start here",
                    estimatedMinutes = 10,
                    expectedOutcome = "Understand the project",
                    tasks = listOf(GeneratedTask(title = "Open the README")),
                    resources = listOf(GeneratedResource(title = "README", url = "https://example.test/readme")),
                ),
            ),
            checkQuestions = listOf(
                GeneratedQuestion(
                    type = CheckQuestionType.MULTIPLE_CHOICE,
                    question = "What does the project do?",
                    options = listOf(
                        GeneratedOption(label = "Onboarding", correct = true),
                        GeneratedOption(label = "Billing", correct = false),
                    ),
                ),
            ),
        )

        val result = factory.createFrom(
            blueprintPath = blueprint,
            userId = UUID.randomUUID(),
            generatedContentByBlueprintPhaseId = mapOf(overview.id to generated),
        )

        val copiedPhase = result.phases.single()
        assertEquals(0, copiedPhase.position)
        val step = copiedPhase.steps.single()
        assertSame(copiedPhase, step.phase)
        assertEquals("Read the README", step.title)
        assertEquals("Open the README", step.tasks.single().title)
        assertEquals("README", step.resources.single().title)
        assertEquals(StepStatus.WAITING, step.status)
        assertEquals(StepType.TASK, step.type)

        val question = copiedPhase.checkQuestions.single()
        assertSame(copiedPhase, question.phase)
        assertEquals("What does the project do?", question.question)
        assertEquals(2, question.options.size)
        assertEquals(true, question.options[0].correct)
        assertEquals(false, question.options[1].correct)
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
            description = "$title description",
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
            graphX = graphX,
            graphY = graphY,
        )
    }
}
