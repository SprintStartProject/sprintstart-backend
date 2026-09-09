package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.OnboardingPathFromBlueprintFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseCheckOptionDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseCheckQuestionDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseContentOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseResourceDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseStepDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.PhaseTaskDto
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingPersonalizationServiceTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val blueprintPathRepository: BlueprintPathRepository = mockk()
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val userApi: UserApi = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val service = OnboardingPersonalizationService(
        onboardingPathRepository = onboardingPathRepository,
        blueprintPathRepository = blueprintPathRepository,
        onboardingPathFactory = OnboardingPathFromBlueprintFactory(),
        onboardingAiClient = onboardingAiClient,
        userApi = userApi,
        json = json,
        transactionManager = transactionManager,
    )
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val authId = "auth|user"
    private val profile = UserOnboardingProfile(
        id = userId,
        projectIds = setOf(projectId),
        projectRoles = emptyMap(),
    )

    @Test
    fun `throws 404 before streaming when user does not exist`() {
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.empty()

        val error = assertThrows<ResponseStatusException> { service.personalize(authId, projectId) }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    @Test
    fun `rejects a project the user is not assigned to with 403`() {
        val otherProjectId = UUID.randomUUID()
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)

        val error = assertThrows<ResponseStatusException> {
            service.personalize(authId, otherProjectId)
        }

        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
        verify(exactly = 0) { blueprintPathRepository.findAllByProjectIdAndStatus(any(), any()) }
        verify(exactly = 0) { onboardingPathRepository.deleteByUserId(any()) }
    }

    @Test
    fun `copies the selected project's active blueprint and replaces existing path`() = runTest {
        val blueprint = blueprint(BlueprintStatus.ACTIVE)
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
        every {
            blueprintPathRepository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)
        } returns listOf(blueprint)
        every { blueprintPathRepository.findById(blueprint.id) } returns Optional.of(blueprint)
        every { onboardingPathRepository.deleteByUserId(userId) } just runs
        every { onboardingPathRepository.save(any()) } answers { firstArg() }

        val events = service.personalize(authId, projectId).toList()

        assertEquals(listOf("path", "done"), events.map { it.type })
        assertEquals(blueprint.id, events.first().path?.blueprintId)
        assertEquals(
            "Introduction",
            events
                .first()
                .path
                ?.phases
                ?.single()
                ?.title,
        )
        verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
        verify(exactly = 1) { onboardingPathRepository.save(any()) }
    }

    @Test
    fun `returns conflict event when the project has several active blueprints`() = runTest {
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
        every {
            blueprintPathRepository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)
        } returns listOf(blueprint(BlueprintStatus.ACTIVE), blueprint(BlueprintStatus.ACTIVE))

        val events = service.personalize(authId, projectId).toList()

        assertEquals("error", events.single().type)
        assertTrue(
            events
                .single()
                .message
                .orEmpty()
                .contains("expected exactly one"),
        )
        verify(exactly = 0) { onboardingPathRepository.deleteByUserId(any()) }
    }

    @Test
    fun `fills AI enhanced phases from the streamed phase assembly`() = runTest {
        val blueprint = aiEnhancedBlueprint()
        val outcome = PhaseContentOutcome(
            status = "assembled",
            steps = listOf(
                PhaseStepDto(
                    title = "Read the README",
                    description = "Start here",
                    estimatedMinutes = 10,
                    expectedOutcome = "Understand the project",
                    tasks = listOf(PhaseTaskDto(title = "Open the README")),
                    resources = listOf(PhaseResourceDto(title = "README", url = "https://example.test/readme")),
                ),
            ),
            checkQuestions = listOf(
                PhaseCheckQuestionDto(
                    position = 0,
                    type = "MULTIPLE_CHOICE",
                    question = "What does the project do?",
                    options = listOf(
                        PhaseCheckOptionDto(label = "Onboarding", correct = true),
                        PhaseCheckOptionDto(label = "Billing", correct = false),
                    ),
                ),
            ),
        )
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
        every {
            blueprintPathRepository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)
        } returns listOf(blueprint)
        every { blueprintPathRepository.findById(blueprint.id) } returns Optional.of(blueprint)
        every { onboardingPathRepository.deleteByUserId(userId) } just runs
        every { onboardingPathRepository.save(any()) } answers { firstArg() }
        every {
            onboardingAiClient.streamPhase(any())
        } returns flowOf(
            AiProgressEvent(type = AiProgressEvent.DONE, result = json.encodeToJsonElement(outcome)),
        )

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        assertEquals(blueprint.id, pathEvent?.path?.blueprintId)
        val phase = pathEvent?.path?.phases?.single()
        assertEquals("Project Overview", phase?.title)
        assertEquals("Read the README", phase?.steps?.single()?.title)
        assertEquals(10, phase?.steps?.single()?.estimatedMinutes)
        assertEquals(1, phase?.checkSummary?.questionCount)
        verify { onboardingAiClient.streamPhase(any()) }
    }

    @Test
    fun `keeps an AI enhanced phase empty when assembly is not available`() = runTest {
        val blueprint = aiEnhancedBlueprint()
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
        every {
            blueprintPathRepository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)
        } returns listOf(blueprint)
        every { blueprintPathRepository.findById(blueprint.id) } returns Optional.of(blueprint)
        every { onboardingPathRepository.deleteByUserId(userId) } just runs
        every { onboardingPathRepository.save(any()) } answers { firstArg() }
        every { onboardingAiClient.streamPhase(any()) } returns flowOf()

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        val phase = pathEvent?.path?.phases?.single()
        assertEquals(blueprint.id, pathEvent?.path?.blueprintId)
        assertEquals(0, phase?.steps?.size)
        assertEquals(0, phase?.checkSummary?.questionCount)
    }

    private fun blueprint(status: BlueprintStatus): BlueprintPath {
        val path = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = projectId,
            title = "Backend onboarding",
            status = status,
        )
        path.blueprintPhases += BlueprintPhase(
            blueprintPath = path,
            position = 0,
            title = "Introduction",
            description = "Meet the project",
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
        )
        return path
    }

    private fun aiEnhancedBlueprint(): BlueprintPath {
        val path = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = projectId,
            title = "AI onboarding",
        )
        path.status = BlueprintStatus.ACTIVE
        path.blueprintPhases += BlueprintPhase(
            blueprintPath = path,
            position = 0,
            title = "Project Overview",
            description = "Overview of the project",
            aiPrompt = "Generate an overview for a new member.",
            type = BlueprintPhaseType.AI_ENHANCED,
        )
        return path
    }
}
