package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.OnboardingConfig
import com.sprintstart.sprintstartbackend.UploadConfig
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.factory.OnboardingPathFromBlueprintFactory
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssemblePhaseRequest
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
import jakarta.persistence.EntityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
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
    private val entityManager: EntityManager = mockk(relaxed = true)
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val service = serviceWith(OnboardingConfig())
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
        every { onboardingPathRepository.flush() } just runs

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
        verify(exactly = 1) { onboardingPathRepository.flush() }
        verify(exactly = 1) { entityManager.persist(any()) }
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
        every { onboardingPathRepository.flush() } just runs
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
        assertEquals(1, phase?.questions?.size)
        assertEquals(GenerationStatus.GENERATED, phase?.generationStatus)
        val generationIssues = pathEvent?.path?.generationIssues.orEmpty()
        assertTrue(generationIssues.isEmpty())
        verify { onboardingAiClient.streamPhase(any()) }
    }

    @Test
    fun `hides a failed AI phase and reports its generation issue`() = runTest {
        val blueprint = aiEnhancedBlueprint()
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
        every {
            blueprintPathRepository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)
        } returns listOf(blueprint)
        every { blueprintPathRepository.findById(blueprint.id) } returns Optional.of(blueprint)
        every { onboardingPathRepository.deleteByUserId(userId) } just runs
        every { onboardingPathRepository.flush() } just runs
        every { onboardingAiClient.streamPhase(any()) } returns flowOf()

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        assertEquals(blueprint.id, pathEvent?.path?.blueprintId)
        val phases = pathEvent?.path?.phases.orEmpty()
        assertTrue(phases.isEmpty())
        val issue = pathEvent?.path?.generationIssues?.single()
        assertEquals("Project Overview", issue?.title)
        assertEquals(GenerationStatus.FAILED, issue?.status)
    }

    @Test
    fun `runs several phase streams concurrently but never beyond the configured limit`() = runTest {
        val blueprint = aiEnhancedBlueprint(listOf("P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7"))
        val service = serviceWith(OnboardingConfig(phaseConcurrency = 4))
        var active = 0
        var maxActive = 0
        expectBlueprint(blueprint)
        every { onboardingAiClient.streamPhase(any()) } answers {
            flow {
                active += 1
                if (active > maxActive) maxActive = active
                delay(50L)
                active -= 1
                emit(doneEvent())
            }
        }

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        assertEquals(8, pathEvent?.path?.phases?.size)
        assertEquals(4, maxActive, "active phase streams exceeded the configured limit")
        assertTrue(maxActive >= 2, "expected overlapping phase streams, saw at most $maxActive at once")
        assertEquals(listOf("path", "done"), events.filter { it.type == "path" || it.type == "done" }.map { it.type })
    }

    @Test
    fun `marks a slow phase as timed out while siblings are still persisted`() = runTest {
        val blueprint = aiEnhancedBlueprint(listOf("Slow", "Fast"))
        expectBlueprint(blueprint)
        every { onboardingAiClient.streamPhase(any()) } answers {
            if (firstArg<AssemblePhaseRequest>().phaseTitle == "Slow") {
                flow { delay(600_000L) }
            } else {
                flowOf(doneEvent())
            }
        }

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        assertEquals(listOf("Fast"), pathEvent?.path?.phases?.map { it.title })
        val issue = pathEvent?.path?.generationIssues?.single()
        assertEquals("Slow", issue?.title)
        assertEquals(GenerationStatus.TIMED_OUT, issue?.status)
        assertTrue(
            events.any {
                it.type == "stage" && it.name == "Slow" && it.detail == "Timed out after 240 seconds"
            },
        )
        assertEquals(1, events.filter { it.type == "path" }.size)
        assertEquals(1, events.filter { it.type == "done" }.size)
    }

    @Test
    fun `overall timeout marks queued and running phases as timed out`() = runTest {
        val blueprint = aiEnhancedBlueprint(listOf("P0", "P1", "P2", "P3", "P4"))
        val service = serviceWith(OnboardingConfig(totalTimeoutSeconds = 1, phaseConcurrency = 2))
        expectBlueprint(blueprint)
        every { onboardingAiClient.streamPhase(any()) } answers {
            flow { delay(600_000L) }
        }

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        assertTrue(pathEvent?.path?.phases.orEmpty().isEmpty())
        val issues = pathEvent?.path?.generationIssues.orEmpty()
        assertEquals(setOf("P0", "P1", "P2", "P3", "P4"), issues.map { it.title }.toSet())
        assertTrue(issues.all { it.status == GenerationStatus.TIMED_OUT })
        assertEquals(1, events.filter { it.type == "path" }.size)
        assertEquals(1, events.filter { it.type == "done" }.size)
    }

    @Test
    fun `a failed phase does not prevent siblings from being persisted`() = runTest {
        val blueprint = aiEnhancedBlueprint(listOf("Broken", "Healthy"))
        expectBlueprint(blueprint)
        every { onboardingAiClient.streamPhase(any()) } answers {
            if (firstArg<AssemblePhaseRequest>().phaseTitle == "Broken") {
                flowOf(AiProgressEvent(type = AiProgressEvent.ERROR, message = "upstream exploded"))
            } else {
                flowOf(doneEvent())
            }
        }

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        assertEquals(listOf("Healthy"), pathEvent?.path?.phases?.map { it.title })
        val issue = pathEvent?.path?.generationIssues?.single()
        assertEquals("Broken", issue?.title)
        assertEquals(GenerationStatus.FAILED, issue?.status)
        assertEquals(1, events.filter { it.type == "path" }.size)
        assertEquals(1, events.filter { it.type == "done" }.size)
    }

    @Test
    fun `keeps blueprint phase order when phases complete out of order`() = runTest {
        val blueprint = aiEnhancedBlueprint(listOf("Slow0", "Fast1", "Medium2"))
        expectBlueprint(blueprint)
        every { onboardingAiClient.streamPhase(any()) } answers {
            when (firstArg<AssemblePhaseRequest>().phaseTitle) {
                "Slow0" -> flow { delay(200L); emit(doneEvent()) }
                "Fast1" -> flowOf(doneEvent())
                else -> flow { delay(100L); emit(doneEvent()) }
            }
        }

        val events = service.personalize(authId, projectId).toList()

        val pathEvent = events.firstOrNull { it.type == "path" }
        assertEquals(
            listOf("Slow0", "Fast1", "Medium2"),
            pathEvent?.path?.phases?.map { it.title },
        )
    }

    private fun serviceWith(onboarding: OnboardingConfig): OnboardingPersonalizationService =
        OnboardingPersonalizationService(
            onboardingPathRepository = onboardingPathRepository,
            blueprintPathRepository = blueprintPathRepository,
            onboardingPathFactory = OnboardingPathFromBlueprintFactory(),
            onboardingAiClient = onboardingAiClient,
            userApi = userApi,
            json = json,
            entityManager = entityManager,
            transactionManager = transactionManager,
            applicationConfig = ApplicationConfig(
                ai = AiConfig(baseUrl = "http://ai.test"),
                github = GithubConfig(baseUrl = "https://api.github.com"),
                crypto = CryptoConfig(masterKey = "test-master-key", salt = "test-salt"),
                upload = UploadConfig(directory = "uploads", maxFileSizeBytes = 10_485_760L),
                onboarding = onboarding,
            ),
        )

    private fun expectBlueprint(blueprint: BlueprintPath) {
        every { userApi.getOnboardingProfileByAuthId(authId) } returns Optional.of(profile)
        every {
            blueprintPathRepository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE)
        } returns listOf(blueprint)
        every { blueprintPathRepository.findById(blueprint.id) } returns Optional.of(blueprint)
        every { onboardingPathRepository.deleteByUserId(userId) } just runs
        every { onboardingPathRepository.flush() } just runs
    }

    private fun doneEvent(): AiProgressEvent = AiProgressEvent(
        type = AiProgressEvent.DONE,
        result = json.encodeToJsonElement(assembledOutcome()),
    )

    private fun assembledOutcome(): PhaseContentOutcome = PhaseContentOutcome(
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
    )

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

    private fun aiEnhancedBlueprint(): BlueprintPath = aiEnhancedBlueprint(listOf("Project Overview"))

    private fun aiEnhancedBlueprint(titles: List<String>): BlueprintPath {
        val path = BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = projectId,
            title = "AI onboarding",
        )
        path.status = BlueprintStatus.ACTIVE
        titles.forEachIndexed { index, title ->
            path.blueprintPhases += BlueprintPhase(
                blueprintPath = path,
                position = index,
                title = title,
                description = "Overview of the project",
                aiPrompt = "Generate an overview for a new member.",
                type = BlueprintPhaseType.AI_ENHANCED,
            )
        }
        return path
    }
}