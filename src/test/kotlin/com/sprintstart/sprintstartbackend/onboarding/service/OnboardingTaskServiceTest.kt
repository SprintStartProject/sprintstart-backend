package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTaskRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class OnboardingTaskServiceTest {
    private val onboardingTaskRepository: OnboardingTaskRepository = mockk()
    private val onboardingStepRepository: OnboardingStepRepository = mockk()
    private val userApi: UserApi = mockk()
    private val service = OnboardingTaskService(onboardingTaskRepository, onboardingStepRepository, userApi)

    private val userId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()
    private val authId = "auth|test-user"

    private fun makeStep(): OnboardingStep {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Phase", description = "Desc")
        return OnboardingStep(
            id = stepId,
            phase = phase,
            position = 0,
            title = "Step",
            description = "Desc",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Outcome",
            status = StepStatus.WAITING,
        )
    }

    private fun makeTask(position: Int = 0): OnboardingTask =
        OnboardingTask(id = taskId, step = makeStep(), position = position, title = "Task", description = "Desc")

    @Nested
    inner class GetOnboardingTasksForMe {
        @Test
        fun `returns tasks for authenticated user`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every {
                onboardingTaskRepository.findAllByStepIdAndStepPhasePathUserId(stepId, userId)
            } returns mutableListOf(makeTask())

            val result = service.getOnboardingTasksForMe(authId, stepId)

            assertEquals(1, result.size)
        }

        @Test
        fun `throws 404 when user not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingTasksForMe(authId, stepId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class CreateOnboardingTaskForMe {
        @Test
        fun `creates task at valid position`() {
            val step = makeStep()
            val task = makeTask()
            val request = CreateOnboardingTaskRequest(position = 0, title = "Task", description = "Desc")

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)
            every { onboardingTaskRepository.countByStepId(step.id) } returns 0
            every {
                onboardingTaskRepository.findByStepIdAndPositionGreaterThanEqualOrderByPositionDesc(step.id, 0)
            } returns mutableListOf()
            every { onboardingTaskRepository.save(any()) } returns task

            val result = service.createOnboardingTaskForMe(authId, stepId, request)

            assertEquals(taskId, result.id)
        }

        @Test
        fun `throws 400 when position out of range`() {
            val step = makeStep()
            val request = CreateOnboardingTaskRequest(position = 5, title = "Task", description = "Desc")

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.of(step)
            every { onboardingTaskRepository.countByStepId(step.id) } returns 2

            assertThrows<ResponseStatusException> {
                service.createOnboardingTaskForMe(authId, stepId, request)
            }.also { assertEquals(400, it.statusCode.value()) }
        }

        @Test
        fun `throws 404 when step not found for user`() {
            val request = CreateOnboardingTaskRequest(position = 0, title = "Task", description = "Desc")
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingStepRepository.findByIdAndPhasePathUserId(stepId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createOnboardingTaskForMe(authId, stepId, request)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingTaskForMe {
        @Test
        fun `returns task for authenticated user`() {
            val task = makeTask()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingTaskRepository.findByIdAndStepPhasePathUserId(taskId, userId) } returns Optional.of(task)

            val result = service.getOnboardingTaskForMe(authId, taskId)

            assertEquals(taskId, result.id)
        }

        @Test
        fun `throws 404 when task not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingTaskRepository.findByIdAndStepPhasePathUserId(taskId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingTaskForMe(authId, taskId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class UpdateOnboardingTaskForMe {
        @Test
        fun `updates task fields`() {
            val task = makeTask()
            val request =
                UpdateOnboardingTaskRequest(
                    position = 0,
                    title = "Updated",
                    description = "Updated Desc",
                    finished = true,
                )

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingTaskRepository.findByIdAndStepPhasePathUserId(taskId, userId) } returns Optional.of(task)
            every { onboardingTaskRepository.countByStepId(task.step.id) } returns 1
            every {
                onboardingTaskRepository.findByStepIdAndPositionBetween(any(), any(), any())
            } returns mutableListOf()

            val result = service.updateOnboardingTaskForMe(authId, taskId, request)

            assertEquals("Updated", result.title)
            assertEquals(true, result.finished)
        }

        @Test
        fun `throws 404 when task not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingTaskRepository.findByIdAndStepPhasePathUserId(taskId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.updateOnboardingTaskForMe(authId, taskId, UpdateOnboardingTaskRequest(0, "t", "d", false))
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingTaskForMe {
        @Test
        fun `deletes task for authenticated user`() {
            val task = makeTask()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingTaskRepository.findByIdAndStepPhasePathUserId(taskId, userId) } returns Optional.of(task)
            every { onboardingTaskRepository.delete(task) } just runs

            service.deleteOnboardingTaskForMe(authId, taskId)

            verify(exactly = 1) { onboardingTaskRepository.delete(task) }
        }

        @Test
        fun `throws 404 when task not found`() {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { onboardingTaskRepository.findByIdAndStepPhasePathUserId(taskId, userId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingTaskForMe(authId, taskId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingTasksByStepId {
        @Test
        fun `returns all tasks for step`() {
            every { onboardingTaskRepository.findAllByStepId(stepId) } returns mutableListOf(makeTask())

            val result = service.getOnboardingTasksByStepId(stepId)

            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class CreateOnboardingTaskForStepId {
        @Test
        fun `creates task for step by stepId`() {
            val step = makeStep()
            val task = makeTask()
            val request = CreateOnboardingTaskRequest(position = 0, title = "Task", description = "Desc")

            every { onboardingStepRepository.findById(stepId) } returns Optional.of(step)
            every { onboardingTaskRepository.countByStepId(step.id) } returns 0
            every {
                onboardingTaskRepository.findByStepIdAndPositionGreaterThanEqualOrderByPositionDesc(step.id, 0)
            } returns mutableListOf()
            every { onboardingTaskRepository.save(any()) } returns task

            val result = service.createOnboardingTaskForStepId(stepId, request)

            assertEquals(taskId, result.id)
        }

        @Test
        fun `throws 404 when step not found`() {
            val request = CreateOnboardingTaskRequest(position = 0, title = "Task", description = "Desc")
            every { onboardingStepRepository.findById(stepId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.createOnboardingTaskForStepId(stepId, request)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class GetOnboardingTaskById {
        @Test
        fun `returns task by id`() {
            val task = makeTask()
            every { onboardingTaskRepository.findById(taskId) } returns Optional.of(task)

            val result = service.getOnboardingTaskById(taskId)

            assertEquals(taskId, result.id)
        }

        @Test
        fun `throws 404 when task not found`() {
            every { onboardingTaskRepository.findById(taskId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.getOnboardingTaskById(taskId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class UpdateOnboardingTaskById {
        @Test
        fun `updates and saves task`() {
            val task = makeTask()
            val request =
                UpdateOnboardingTaskRequest(position = 0, title = "New", description = "New Desc", finished = true)

            every { onboardingTaskRepository.findById(taskId) } returns Optional.of(task)
            every { onboardingTaskRepository.countByStepId(task.step.id) } returns 1
            every {
                onboardingTaskRepository.findByStepIdAndPositionBetween(any(), any(), any())
            } returns mutableListOf()
            every { onboardingTaskRepository.save(task) } returns task

            val result = service.updateOnboardingTaskById(taskId, request)

            assertEquals("New", result.title)
            verify(exactly = 1) { onboardingTaskRepository.save(task) }
        }

        @Test
        fun `throws 404 when task not found`() {
            every { onboardingTaskRepository.findById(taskId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.updateOnboardingTaskById(taskId, UpdateOnboardingTaskRequest(0, "t", "d", false))
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }

    @Nested
    inner class DeleteOnboardingTaskById {
        @Test
        fun `deletes task and shifts subsequent tasks`() {
            val task = makeTask(1)
            val laterTask = makeTask(2)

            every { onboardingTaskRepository.findById(taskId) } returns Optional.of(task)
            every {
                onboardingTaskRepository.findAllByStepIdAndPositionGreaterThan(task.step.id, 1)
            } returns mutableListOf(laterTask)
            every { onboardingTaskRepository.delete(task) } just runs

            service.deleteOnboardingTaskById(taskId)

            assertEquals(1, laterTask.position)
            verify(exactly = 1) { onboardingTaskRepository.delete(task) }
        }

        @Test
        fun `throws 404 when task not found`() {
            every { onboardingTaskRepository.findById(taskId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                service.deleteOnboardingTaskById(taskId)
            }.also { assertEquals(404, it.statusCode.value()) }
        }
    }
}
