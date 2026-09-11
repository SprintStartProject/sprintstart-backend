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
import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `hides an empty AI phase and contracts its blockers for the learner path`() {
        val blueprint = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            title = "Contracted onboarding",
        )
        val foundation = phase(blueprint, 0, "Foundation", 0.0, 0.0)
        val unavailable = BlueprintPhase(
            blueprintPath = blueprint,
            position = 1,
            title = "Generated role tasks",
            description = "Generated tasks",
            aiPrompt = "Generate role tasks",
            type = BlueprintPhaseType.AI_ENHANCED,
        )
        val delivery = phase(blueprint, 2, "Delivery", 2.0, 2.0)
        unavailable.blockedBy += foundation
        delivery.blockedBy += unavailable
        blueprint.blueprintPhases += listOf(foundation, unavailable, delivery)

        val result = factory.createFrom(
            blueprintPath = blueprint,
            userId = UUID.randomUUID(),
            generatedContentByBlueprintPhaseId = mapOf(unavailable.id to GeneratedPhaseContent()),
            generationStatusByBlueprintPhaseId = mapOf(unavailable.id to GenerationStatus.SKIPPED),
        )

        assertEquals(3, result.phases.size)
        val persistedUnavailable = result.phases.single { it.title == "Generated role tasks" }
        val persistedDelivery = result.phases.single { it.title == "Delivery" }
        val persistedFoundation = result.phases.single { it.title == "Foundation" }
        assertEquals(GenerationStatus.SKIPPED, persistedUnavailable.generationStatus)
        assertEquals(setOf(persistedFoundation.id), persistedDelivery.blockedBy.map { it.id }.toSet())

        val response = result.toGetForUserResponse()
        assertEquals(listOf("Foundation", "Delivery"), response.phases.map { it.title })
        assertEquals("Generated role tasks", response.generationIssues.single().title)
        assertEquals(GenerationStatus.SKIPPED, response.generationIssues.single().status)
    }

    @Test
    fun `hides a timed out AI phase and reports it as a generation issue`() {
        val blueprint = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = UUID.randomUUID(),
            title = "Timed out onboarding",
        )
        val foundation = phase(blueprint, 0, "Foundation", 0.0, 0.0)
        val timedOut = BlueprintPhase(
            blueprintPath = blueprint,
            position = 1,
            title = "Generated role tasks",
            description = "Generated tasks",
            aiPrompt = "Generate role tasks",
            type = BlueprintPhaseType.AI_ENHANCED,
        )
        val delivery = phase(blueprint, 2, "Delivery", 2.0, 2.0)
        timedOut.blockedBy += foundation
        delivery.blockedBy += timedOut
        blueprint.blueprintPhases += listOf(foundation, timedOut, delivery)

        val result = factory.createFrom(
            blueprintPath = blueprint,
            userId = UUID.randomUUID(),
            generatedContentByBlueprintPhaseId = mapOf(timedOut.id to GeneratedPhaseContent()),
            generationStatusByBlueprintPhaseId = mapOf(timedOut.id to GenerationStatus.TIMED_OUT),
        )

        assertEquals(3, result.phases.size)
        val persistedTimedOut = result.phases.single { it.title == "Generated role tasks" }
        val persistedDelivery = result.phases.single { it.title == "Delivery" }
        val persistedFoundation = result.phases.single { it.title == "Foundation" }
        assertEquals(GenerationStatus.TIMED_OUT, persistedTimedOut.generationStatus)
        assertEquals(setOf(persistedFoundation.id), persistedDelivery.blockedBy.map { it.id }.toSet())

        val response = result.toGetForUserResponse()
        assertEquals(listOf("Foundation", "Delivery"), response.phases.map { it.title })
        assertEquals("Generated role tasks", response.generationIssues.single().title)
        assertEquals(GenerationStatus.TIMED_OUT, response.generationIssues.single().status)
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

    @Test
    fun `wires generated dependency edges by key onto the onboarding phase`() {
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
                GeneratedStep(key = "s1", title = "Read the README"),
                GeneratedStep(
                    key = "s2",
                    title = "Configure the environment",
                    blockedBy = listOf("s1"),
                ),
                GeneratedStep(
                    key = "s3",
                    title = "Depends on unknown and self",
                    blockedBy = listOf("nope", "s3"),
                ),
            ),
            checkQuestions = listOf(
                GeneratedQuestion(
                    key = "q1",
                    type = CheckQuestionType.MULTIPLE_CHOICE,
                    question = "What must be done first?",
                    options = listOf(
                        GeneratedOption(label = "Read the README", correct = true),
                        GeneratedOption(label = "Configure", correct = false),
                    ),
                    blockedBy = listOf("s1"),
                ),
            ),
        )

        val result = factory.createFrom(
            blueprintPath = blueprint,
            userId = UUID.randomUUID(),
            generatedContentByBlueprintPhaseId = mapOf(overview.id to generated),
        )

        val phase = result.phases.single()
        val firstStep = phase.steps[0]
        val secondStep = phase.steps[1]
        val thirdStep = phase.steps[2]
        assertEquals(setOf(firstStep.id), secondStep.blockedBy.map { it.id }.toSet())
        assertTrue(thirdStep.blockedBy.isEmpty())
        val question = phase.checkQuestions.single()
        assertEquals(setOf(firstStep.id), question.blockedBy.map { it.id }.toSet())
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
