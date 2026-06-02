package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTaskRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class OnboardingServiceTest {
    private lateinit var onboardingPathRepository: OnboardingPathRepository
    private lateinit var onboardingPhaseRepository: OnboardingPhaseRepository
    private lateinit var onboardingStepRepository: OnboardingStepRepository
    private lateinit var onboardingTaskRepository: OnboardingTaskRepository
    private lateinit var onboardingResourceRepository: OnboardingResourceRepository
    private lateinit var userApi: UserApi
    private lateinit var onboardingService: OnboardingService

    private val userId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val pathId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val phaseId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val stepId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val taskId: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val resourceId: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")

    @BeforeEach
    fun setUp() {
        onboardingPathRepository = mockk()
        onboardingPhaseRepository = mockk()
        onboardingStepRepository = mockk()
        onboardingTaskRepository = mockk()
        onboardingResourceRepository = mockk()
        userApi = mockk()

        onboardingService = OnboardingService(
            onboardingPathRepository = onboardingPathRepository,
            onboardingPhaseRepository = onboardingPhaseRepository,
            onboardingStepRepository = onboardingStepRepository,
            onboardingTaskRepository = onboardingTaskRepository,
            onboardingResourceRepository = onboardingResourceRepository,
            userApi = userApi,
        )
    }

    // -------------------------------------------------------------------------
    // Paths
    // -------------------------------------------------------------------------

    @Test
    fun `createOnboardingPathForUser should save path if user exists`() {
        val savedPathSlot = slot<OnboardingPath>()

        every { userApi.exists(userId) } returns true
        every { onboardingPathRepository.save(capture(savedPathSlot)) } answers { savedPathSlot.captured }

        val response = onboardingService.createOnboardingPathForUser(userId)

        assertEquals(userId, savedPathSlot.captured.userId)
        assertEquals(userId, response.userId)

        verify(exactly = 1) { userApi.exists(userId) }
        verify(exactly = 1) { onboardingPathRepository.save(any()) }
    }

    @Test
    fun `createOnboardingPathForUser should throw 404 if user does not exist`() {
        every { userApi.exists(userId) } returns false

        val exception = assertThrows<ResponseStatusException> {
            onboardingService.createOnboardingPathForUser(userId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)

        verify(exactly = 1) { userApi.exists(userId) }
        verify(exactly = 0) { onboardingPathRepository.save(any()) }
    }

    @Test
    fun `getOnboardingPath should return path`() {
        val path = path()

        every { onboardingPathRepository.findById(pathId) } returns Optional.of(path)

        val response = onboardingService.getOnboardingPath(pathId)

        assertEquals(pathId, response.id)
        assertEquals(userId, response.userId)

        verify(exactly = 1) { onboardingPathRepository.findById(pathId) }
    }

    @Test
    fun `getOnboardingPath should throw 404 if path does not exist`() {
        every { onboardingPathRepository.findById(pathId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            onboardingService.getOnboardingPath(pathId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `getOnboardingPathByUserId should return path if user and path exist`() {
        val path = path()

        every { userApi.exists(userId) } returns true
        every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)

        val response = onboardingService.getOnboardingPathByUserId(userId)

        assertEquals(pathId, response.id)
        assertEquals(userId, response.userId)

        verify(exactly = 1) { userApi.exists(userId) }
        verify(exactly = 1) { onboardingPathRepository.findOnboardingPathByUserId(userId) }
    }

    @Test
    fun `getOnboardingPathByUserId should throw 404 if user does not exist`() {
        every { userApi.exists(userId) } returns false

        val exception = assertThrows<ResponseStatusException> {
            onboardingService.getOnboardingPathByUserId(userId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)

        verify(exactly = 1) { userApi.exists(userId) }
        verify(exactly = 0) { onboardingPathRepository.findOnboardingPathByUserId(any()) }
    }

    @Test
    fun `deleteOnboardingPathByUserId should delete path if user exists`() {
        every { userApi.exists(userId) } returns true
        every { onboardingPathRepository.deleteByUserId(userId) } just Runs

        onboardingService.deleteOnboardingPathByUserId(userId)

        verify(exactly = 1) { userApi.exists(userId) }
        verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
    }

    // -------------------------------------------------------------------------
    // Phases
    // -------------------------------------------------------------------------

    @Test
    fun `createOnboardingPhaseForPathId should shift phases and save new phase`() {
        val path = path()
        val existingPhase = phase(position = 1)
        val request = CreateOnboardingPhaseRequest(
            position = 1,
            title = "New phase",
            description = "New phase description",
        )
        val savedPhaseSlot = slot<OnboardingPhase>()

        every { onboardingPathRepository.findById(pathId) } returns Optional.of(path)
        every {
            onboardingPhaseRepository.findByPath_IdAndPositionGreaterThanEqual(pathId, 1)
        } returns mutableListOf(existingPhase)
        every { onboardingPhaseRepository.saveAll(listOf(existingPhase)) } returns listOf(existingPhase)
        every { onboardingPhaseRepository.save(capture(savedPhaseSlot)) } answers { savedPhaseSlot.captured }

        val response = onboardingService.createOnboardingPhaseForPathId(pathId, request)

        assertEquals(2, existingPhase.position)
        assertSame(path, savedPhaseSlot.captured.path)
        assertEquals(1, savedPhaseSlot.captured.position)
        assertEquals("New phase", savedPhaseSlot.captured.title)
        assertEquals("New phase description", savedPhaseSlot.captured.description)
        assertEquals("New phase", response.title)

        verify(exactly = 1) { onboardingPhaseRepository.saveAll(listOf(existingPhase)) }
        verify(exactly = 1) { onboardingPhaseRepository.save(any()) }
    }

    @Test
    fun `createOnboardingPhaseForPathId should throw 404 if path does not exist`() {
        val request = CreateOnboardingPhaseRequest(
            position = 1,
            title = "New phase",
            description = "New phase description",
        )

        every { onboardingPathRepository.findById(pathId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            onboardingService.createOnboardingPhaseForPathId(pathId, request)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)

        verify(exactly = 0) { onboardingPhaseRepository.save(any()) }
    }

    @Test
    fun `updateOnboardingPhase should move phase forward and shift phases back`() {
        val phase = phase(position = 1)
        val shiftedPhase = phase(position = 2)
        val request = UpdateOnboardingPhaseRequest(
            position = 2,
            title = "Updated phase",
            description = "Updated phase description",
        )

        every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)
        every {
            onboardingPhaseRepository.findByPath_IdAndPositionBetween(pathId, 2, 2)
        } returns mutableListOf(shiftedPhase)
        every { onboardingPhaseRepository.saveAll(listOf(shiftedPhase)) } returns listOf(shiftedPhase)
        every { onboardingPhaseRepository.save(phase) } returns phase

        val response = onboardingService.updateOnboardingPhase(phaseId, request)

        assertEquals(1, shiftedPhase.position)
        assertEquals(2, phase.position)
        assertEquals("Updated phase", phase.title)
        assertEquals("Updated phase description", phase.description)
        assertEquals(2, response.position)

        verify(exactly = 1) {
            onboardingPhaseRepository.findByPath_IdAndPositionBetween(pathId, 2, 2)
        }
        verify(exactly = 1) { onboardingPhaseRepository.saveAll(listOf(shiftedPhase)) }
        verify(exactly = 1) { onboardingPhaseRepository.save(phase) }
    }

    @Test
    fun `updateOnboardingPhase should move phase backward and shift phases forward`() {
        val phase = phase(position = 3)
        val shiftedPhase = phase(position = 2)
        val request = UpdateOnboardingPhaseRequest(
            position = 2,
            title = "Updated phase",
            description = "Updated phase description",
        )

        every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)
        every {
            onboardingPhaseRepository.findByPath_IdAndPositionBetween(pathId, 2, 2)
        } returns mutableListOf(shiftedPhase)
        every { onboardingPhaseRepository.saveAll(listOf(shiftedPhase)) } returns listOf(shiftedPhase)
        every { onboardingPhaseRepository.save(phase) } returns phase

        onboardingService.updateOnboardingPhase(phaseId, request)

        assertEquals(3, shiftedPhase.position)
        assertEquals(2, phase.position)
    }

    @Test
    fun `deleteOnboardingPhase should shift following phases back and delete phase`() {
        val phaseToDelete = phase(position = 1)
        val followingPhase = phase(position = 2)

        every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phaseToDelete)
        every {
            onboardingPhaseRepository.findByPath_IdAndPositionGreaterThan(pathId, 1)
        } returns mutableListOf(followingPhase)
        every { onboardingPhaseRepository.saveAll(listOf(followingPhase)) } returns listOf(followingPhase)
        every { onboardingPhaseRepository.delete(phaseToDelete) } just Runs

        onboardingService.deleteOnboardingPhase(phaseId)

        assertEquals(1, followingPhase.position)

        verify(exactly = 1) { onboardingPhaseRepository.saveAll(listOf(followingPhase)) }
        verify(exactly = 1) { onboardingPhaseRepository.delete(phaseToDelete) }
    }

    // -------------------------------------------------------------------------
    // Steps
    // -------------------------------------------------------------------------

    @Test
    fun `createOnboardingStepForPhaseId should shift steps and save new step with WAITING status`() {
        val phase = phase()
        val existingStep = step(phase = phase, position = 1)
        val request = CreateOnboardingStepRequest(
            position = 1,
            title = "Read docs",
            description = "Read internal documentation",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Knows the basics",
        )
        val savedStepSlot = slot<OnboardingStep>()

        every { onboardingPhaseRepository.findById(phaseId) } returns Optional.of(phase)
        every {
            onboardingStepRepository.findByPhase_IdAndPositionGreaterThanEqual(phaseId, 1)
        } returns mutableListOf(existingStep)
        every { onboardingStepRepository.saveAll(listOf(existingStep)) } returns listOf(existingStep)
        every { onboardingStepRepository.save(capture(savedStepSlot)) } answers { savedStepSlot.captured }

        val response = onboardingService.createOnboardingStepForPhaseId(phaseId, request)

        assertEquals(2, existingStep.position)
        assertSame(phase, savedStepSlot.captured.phase)
        assertEquals(1, savedStepSlot.captured.position)
        assertEquals(StepStatus.WAITING, savedStepSlot.captured.status)
        assertEquals(StepStatus.WAITING, response.status)

        verify(exactly = 1) { onboardingStepRepository.saveAll(listOf(existingStep)) }
        verify(exactly = 1) { onboardingStepRepository.save(any()) }
    }

    @Test
    fun `updateOnboardingStep should update fields and move step forward`() {
        val step = step(position = 1, status = StepStatus.WAITING)
        val shiftedStep = step(position = 2)
        val request = UpdateOnboardingStepRequest(
            position = 2,
            title = "Updated step",
            description = "Updated step description",
            type = StepType.VIDEO,
            estimatedMinutes = 45,
            expectedOutcome = "Updated outcome",
            status = StepStatus.WAITING,
            skipReason = null,
        )

        every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
        every {
            onboardingStepRepository.findByPhase_IdAndPositionBetween(phaseId, 2, 2)
        } returns mutableListOf(shiftedStep)
        every { onboardingStepRepository.saveAll(listOf(shiftedStep)) } returns listOf(shiftedStep)
        every { onboardingStepRepository.save(step) } returns step

        val response = onboardingService.updateOnboardingStep(stepId, request)

        assertEquals(1, shiftedStep.position)
        assertEquals(2, step.position)
        assertEquals("Updated step", step.title)
        assertEquals(StepType.VIDEO, step.type)
        assertEquals(45, step.estimatedMinutes)
        assertEquals("Updated outcome", step.expectedOutcome)
        assertEquals(2, response.position)

        verify(exactly = 1) { onboardingStepRepository.saveAll(listOf(shiftedStep)) }
        verify(exactly = 1) { onboardingStepRepository.save(step) }
    }

    @Test
    fun `updateOnboardingStep should set completedAt when status changes to FINISHED`() {
        val step = step(status = StepStatus.WAITING)
        val request = updateStepRequest(status = StepStatus.FINISHED)

        every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
        every { onboardingStepRepository.saveAll(emptyList<OnboardingStep>()) } returns emptyList()
        every { onboardingStepRepository.save(step) } returns step

        onboardingService.updateOnboardingStep(stepId, request)

        assertEquals(StepStatus.FINISHED, step.status)
        assertNotNull(step.completedAt)
        assertNull(step.skipReason)
    }

    @Test
    fun `updateOnboardingStep should set completedAt and default skip reason when changed to SKIPPED without reason`() {
        val step = step(status = StepStatus.WAITING)
        val request = updateStepRequest(
            status = StepStatus.SKIPPED,
            skipReason = null,
        )

        every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
        every { onboardingStepRepository.saveAll(emptyList<OnboardingStep>()) } returns emptyList()
        every { onboardingStepRepository.save(step) } returns step

        onboardingService.updateOnboardingStep(stepId, request)

        assertEquals(StepStatus.SKIPPED, step.status)
        assertNotNull(step.completedAt)
        assertEquals("No reason given", step.skipReason)
    }

    @Test
    fun `updateOnboardingStep should clear completedAt and skip reason when status changes to WAITING`() {
        val step = step(
            status = StepStatus.FINISHED,
            completedAt = Instant.parse("2026-06-01T10:00:00Z"),
            skipReason = "Old reason",
        )
        val request = updateStepRequest(status = StepStatus.WAITING)

        every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
        every { onboardingStepRepository.saveAll(emptyList<OnboardingStep>()) } returns emptyList()
        every { onboardingStepRepository.save(step) } returns step

        onboardingService.updateOnboardingStep(stepId, request)

        assertEquals(StepStatus.WAITING, step.status)
        assertNull(step.completedAt)
        assertEquals("", step.skipReason)
    }

    @Test
    fun `deleteOnboardingStep should shift following steps back and delete step`() {
        val stepToDelete = step(position = 1)
        val followingStep = step(position = 2)

        every { onboardingStepRepository.findById(stepId) } returns Optional.of(stepToDelete)
        every {
            onboardingStepRepository.findByPhase_IdAndPositionGreaterThan(phaseId, 1)
        } returns mutableListOf(followingStep)
        every { onboardingStepRepository.saveAll(listOf(followingStep)) } returns listOf(followingStep)
        every { onboardingStepRepository.delete(stepToDelete) } just Runs

        onboardingService.deleteOnboardingStep(stepId)

        assertEquals(1, followingStep.position)

        verify(exactly = 1) { onboardingStepRepository.saveAll(listOf(followingStep)) }
        verify(exactly = 1) { onboardingStepRepository.delete(stepToDelete) }
    }

    // -------------------------------------------------------------------------
    // Tasks
    // -------------------------------------------------------------------------

    @Test
    fun `createOnboardingTaskForStepId should shift tasks and save new task`() {
        val step = step()
        val existingTask = task(step = step, position = 1)
        val request = CreateOnboardingTaskRequest(
            position = 1,
            title = "Create account",
            description = "Create internal account",
        )
        val savedTaskSlot = slot<OnboardingTask>()

        every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
        every {
            onboardingTaskRepository.findByStep_IdAndPositionGreaterThanEqual(stepId, 1)
        } returns mutableListOf(existingTask)
        every { onboardingTaskRepository.saveAll(listOf(existingTask)) } returns listOf(existingTask)
        every { onboardingTaskRepository.save(capture(savedTaskSlot)) } answers { savedTaskSlot.captured }

        val response = onboardingService.createOnboardingTaskForStepId(stepId, request)

        assertEquals(2, existingTask.position)
        assertSame(step, savedTaskSlot.captured.step)
        assertEquals(1, savedTaskSlot.captured.position)
        assertEquals("Create account", savedTaskSlot.captured.title)
        assertFalse(savedTaskSlot.captured.finished)
        assertEquals("Create account", response.title)

        verify(exactly = 1) { onboardingTaskRepository.saveAll(listOf(existingTask)) }
        verify(exactly = 1) { onboardingTaskRepository.save(any()) }
    }

    @Test
    fun `updateOnboardingTask should update task and shift tasks`() {
        val task = task(position = 1, finished = false)
        val shiftedTask = task(position = 2)
        val request = UpdateOnboardingTaskRequest(
            position = 2,
            title = "Updated task",
            description = "Updated task description",
            finished = true,
        )

        every { onboardingTaskRepository.findById(taskId) } returns Optional.of(task)
        every {
            onboardingTaskRepository.findByStep_IdAndPositionBetween(stepId, 2, 2)
        } returns mutableListOf(shiftedTask)
        every { onboardingTaskRepository.saveAll(listOf(shiftedTask)) } returns listOf(shiftedTask)
        every { onboardingTaskRepository.save(task) } returns task

        val response = onboardingService.updateOnboardingTask(taskId, request)

        assertEquals(1, shiftedTask.position)
        assertEquals(2, task.position)
        assertEquals("Updated task", task.title)
        assertEquals("Updated task description", task.description)
        assertTrue(task.finished)
        assertTrue(response.finished)

        verify(exactly = 1) { onboardingTaskRepository.saveAll(listOf(shiftedTask)) }
        verify(exactly = 1) { onboardingTaskRepository.save(task) }
    }

    @Test
    fun `deleteOnboardingTask should shift following tasks back and delete task`() {
        val taskToDelete = task(position = 1)
        val followingTask = task(position = 2)

        every { onboardingTaskRepository.findById(taskId) } returns Optional.of(taskToDelete)
        every {
            onboardingTaskRepository.findByStep_IdAndPositionGreaterThan(stepId, 1)
        } returns mutableListOf(followingTask)
        every { onboardingTaskRepository.saveAll(listOf(followingTask)) } returns listOf(followingTask)
        every { onboardingTaskRepository.delete(taskToDelete) } just Runs

        onboardingService.deleteOnboardingTask(taskId)

        assertEquals(1, followingTask.position)

        verify(exactly = 1) { onboardingTaskRepository.saveAll(listOf(followingTask)) }
        verify(exactly = 1) { onboardingTaskRepository.delete(taskToDelete) }
    }

    // -------------------------------------------------------------------------
    // Resources
    // -------------------------------------------------------------------------

    @Test
    fun `createOnboardingResourceForStepId should save resource for step`() {
        val step = step()
        val request = CreateOnboardingResourceRequest(
            title = "Docs",
            description = "Internal docs",
            url = "https://example.com/docs",
        )
        val savedResourceSlot = slot<OnboardingResource>()

        every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
        every { onboardingResourceRepository.save(capture(savedResourceSlot)) } answers { savedResourceSlot.captured }

        val response = onboardingService.createOnboardingResourceForStepId(stepId, request)

        assertSame(step, savedResourceSlot.captured.step)
        assertEquals("Docs", savedResourceSlot.captured.title)
        assertEquals("Internal docs", savedResourceSlot.captured.description)
        assertEquals("https://example.com/docs", savedResourceSlot.captured.url)
        assertEquals("Docs", response.title)

        verify(exactly = 1) { onboardingResourceRepository.save(any()) }
    }

    @Test
    fun `updateOnboardingResource should update resource`() {
        val resource = resource()
        val request = UpdateOnboardingResourceRequest(
            title = "Updated docs",
            description = "Updated internal docs",
            url = "https://example.com/updated-docs",
        )

        every { onboardingResourceRepository.findById(resourceId) } returns Optional.of(resource)
        every { onboardingResourceRepository.save(resource) } returns resource

        val response = onboardingService.updateOnboardingResource(resourceId, request)

        assertEquals("Updated docs", resource.title)
        assertEquals("Updated internal docs", resource.description)
        assertEquals("https://example.com/updated-docs", resource.url)
        assertEquals("Updated docs", response.title)

        verify(exactly = 1) { onboardingResourceRepository.save(resource) }
    }

    @Test
    fun `deleteOnboardingResource should delete resource`() {
        val resource = resource()

        every { onboardingResourceRepository.findById(resourceId) } returns Optional.of(resource)
        every { onboardingResourceRepository.delete(resource) } just Runs

        onboardingService.deleteOnboardingResource(resourceId)

        verify(exactly = 1) { onboardingResourceRepository.delete(resource) }
    }

    @Test
    fun `deleteOnboardingResource should throw 404 if resource does not exist`() {
        every { onboardingResourceRepository.findById(resourceId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            onboardingService.deleteOnboardingResource(resourceId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)

        verify(exactly = 0) { onboardingResourceRepository.delete(any()) }
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private fun path(
        id: UUID = pathId,
        userId: UUID = this.userId,
    ): OnboardingPath {
        return OnboardingPath(
            id = id,
            userId = userId,
        )
    }

    private fun phase(
        id: UUID = phaseId,
        path: OnboardingPath = path(),
        position: Int = 1,
        title: String = "Phase",
        description: String = "Phase description",
    ): OnboardingPhase {
        return OnboardingPhase(
            id = id,
            path = path,
            position = position,
            title = title,
            description = description,
        )
    }

    private fun step(
        id: UUID = stepId,
        phase: OnboardingPhase = phase(),
        position: Int = 1,
        title: String = "Step",
        description: String = "Step description",
        type: StepType = StepType.DOCUMENT,
        estimatedMinutes: Int = 30,
        expectedOutcome: String = "Expected outcome",
        status: StepStatus = StepStatus.WAITING,
        completedAt: Instant? = null,
        skipReason: String? = null,
    ): OnboardingStep {
        return OnboardingStep(
            id = id,
            phase = phase,
            position = position,
            title = title,
            description = description,
            type = type,
            estimatedMinutes = estimatedMinutes,
            expectedOutcome = expectedOutcome,
            status = status,
            completedAt = completedAt,
            skipReason = skipReason,
        )
    }

    private fun task(
        id: UUID = taskId,
        step: OnboardingStep = step(),
        position: Int = 1,
        title: String = "Task",
        description: String = "Task description",
        finished: Boolean = false,
    ): OnboardingTask {
        return OnboardingTask(
            id = id,
            step = step,
            position = position,
            title = title,
            description = description,
            finished = finished,
        )
    }

    private fun resource(
        id: UUID = resourceId,
        step: OnboardingStep = step(),
        title: String = "Resource",
        description: String = "Resource description",
        url: String = "https://example.com",
    ): OnboardingResource {
        return OnboardingResource(
            id = id,
            step = step,
            title = title,
            description = description,
            url = url,
        )
    }

    private fun updateStepRequest(
        position: Int = 1,
        title: String = "Updated step",
        description: String = "Updated step description",
        type: StepType = StepType.DOCUMENT,
        estimatedMinutes: Int = 30,
        expectedOutcome: String = "Updated outcome",
        status: StepStatus = StepStatus.WAITING,
        skipReason: String? = null,
    ): UpdateOnboardingStepRequest {
        return UpdateOnboardingStepRequest(
            position = position,
            title = title,
            description = description,
            type = type,
            estimatedMinutes = estimatedMinutes,
            expectedOutcome = expectedOutcome,
            status = status,
            skipReason = skipReason,
        )
    }
}
