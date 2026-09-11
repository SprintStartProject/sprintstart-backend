package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.CreateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.DeleteBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintTaskRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals

class BlueprintTaskServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val blueprintTaskRepository: BlueprintTaskRepository = mockk()
    private val service = BlueprintTaskService(blueprintAccessService, blueprintTaskRepository)

    private fun makeTask(
        step: BlueprintStep = blueprintStepFixture(),
        position: Int = 0,
        revision: Long = 0,
        title: String = "Task",
        description: String = "Description",
    ): BlueprintTask {
        return BlueprintTask(
            blueprintStep = step,
            position = position,
            title = title,
            description = description,
        ).also { it.revision = revision }
    }

    @Nested
    inner class GetBlueprintTasksForStep {
        @Test
        fun `returns mapped tasks for global scope`() {
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 0, revision = 2, title = "First", description = "Details")
            every {
                blueprintTaskRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintStepId(step.id)
            } returns mutableListOf(task)

            val result = service.getBlueprintTasksForStep(BlueprintScope.Global, step.id)

            assertEquals(task.id, result.single().id)
            assertEquals(step.id, result.single().blueprintStepId)
            assertEquals(2, result.single().revision)
            assertEquals(0, result.single().position)
            assertEquals("First", result.single().title)
            assertEquals("Details", result.single().description)
            verify(exactly = 1) {
                blueprintTaskRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintStepId(step.id)
            }
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val step = blueprintStepFixture(blueprintPhaseFixture(blueprintPathFixture(projectId)))
            val task = makeTask(step, position = 0, revision = 1, title = "Task", description = "Description")
            every {
                blueprintTaskRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(projectId, step.id)
            } returns mutableListOf(task)

            val result = service.getBlueprintTasksForStep(BlueprintScope.Project(projectId), step.id)

            assertEquals(listOf(task.id), result.map { it.id })
            verify(exactly = 1) {
                blueprintTaskRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(projectId, step.id)
            }
        }
    }

    @Nested
    inner class GetBlueprintTaskById {
        @Test
        fun `returns mapped task for global scope`() {
            val task = makeTask(position = 0, revision = 3, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedTask(BlueprintScope.Global, task.id) } returns task

            val result = service.getBlueprintTaskById(BlueprintScope.Global, task.id)

            assertEquals(task.id, result.id)
            assertEquals(task.blueprintStep.id, result.blueprintStepId)
            assertEquals(3, result.revision)
            assertEquals("Task", result.title)
            assertEquals("Description", result.description)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedTask(BlueprintScope.Global, task.id) }
        }

        @Test
        fun `returns mapped task for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val task = makeTask()
            every { blueprintAccessService.getAuthorizedTask(scope, task.id) } returns task

            val result = service.getBlueprintTaskById(scope, task.id)

            assertEquals(task.id, result.id)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedTask(scope, task.id) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val taskId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedTask(BlueprintScope.Global, taskId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.getBlueprintTaskById(BlueprintScope.Global, taskId)
            }
        }
    }

    @Nested
    inner class CreateBlueprintTaskForStep {
        @Test
        fun `inserts at the requested position after shifting siblings right`() {
            val step = blueprintStepFixture()
            val sibling = makeTask(step, position = 1)
            every { blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id) } returns step
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 2
            every {
                blueprintTaskRepository
                    .findByBlueprintStepIdAndPositionGreaterThanEqualOrderByPositionDesc(step.id, 1)
            } returns mutableListOf(sibling)
            every { blueprintTaskRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintTaskForStep(
                    BlueprintScope.Global,
                    step.id,
                    CreateBlueprintTaskRequest(position = 1, title = "New", description = "Details"),
                )

            assertEquals(step.id, result.blueprintStepId)
            assertEquals(1, result.position)
            assertEquals("New", result.title)
            assertEquals("Details", result.description)
            assertEquals(2, sibling.position)
            verify(exactly = 1) { blueprintTaskRepository.save(any()) }
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id) }
        }

        @Test
        fun `appends at the end of the contiguous range for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val step = blueprintStepFixture()
            every { blueprintAccessService.getAuthorizedEditableStep(scope, step.id) } returns step
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 1
            every {
                blueprintTaskRepository
                    .findByBlueprintStepIdAndPositionGreaterThanEqualOrderByPositionDesc(step.id, 1)
            } returns mutableListOf()
            every { blueprintTaskRepository.save(any()) } answers { firstArg() }

            val result =
                service.createBlueprintTaskForStep(
                    scope,
                    step.id,
                    CreateBlueprintTaskRequest(position = 1, title = "Task", description = "Description"),
                )

            assertEquals(1, result.position)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableStep(scope, step.id) }
            verify(exactly = 1) { blueprintTaskRepository.save(any()) }
        }

        @Test
        fun `rejects insertion outside the contiguous position range`() {
            val step = blueprintStepFixture()
            every { blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id) } returns step
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 0

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintTaskForStep(
                    BlueprintScope.Global,
                    step.id,
                    CreateBlueprintTaskRequest(position = 1, title = "Task", description = "Description"),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.save(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val stepId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, stepId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.createBlueprintTaskForStep(
                    BlueprintScope.Global,
                    stepId,
                    CreateBlueprintTaskRequest(position = 0, title = "Task", description = "Description"),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.save(any()) }
        }
    }

    @Nested
    inner class UpdateBlueprintTaskById {
        @Test
        fun `updates fields and decreases siblings when moving to a higher position`() {
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 0, revision = 2, title = "Old", description = "Old")
            val sibling = makeTask(step, position = 1)
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 2
            every {
                blueprintTaskRepository.findByBlueprintStepIdAndPositionBetween(step.id, 1, 1)
            } returns mutableListOf(sibling)
            every { blueprintTaskRepository.save(any()) } answers { firstArg() }

            val result =
                service.updateBlueprintTaskById(
                    BlueprintScope.Global,
                    task.id,
                    UpdateBlueprintTaskRequest(revision = 2, position = 1, title = "New", description = "Details"),
                )

            assertEquals("New", result.title)
            assertEquals("Details", result.description)
            assertEquals(1, result.position)
            assertEquals(2, result.revision)
            assertEquals(0, sibling.position)
            verify(exactly = 1) { blueprintTaskRepository.save(any()) }
        }

        @Test
        fun `increases siblings when moving to a lower position for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 2, revision = 1, title = "Task", description = "Description")
            val sibling = makeTask(step, position = 0)
            every { blueprintAccessService.getAuthorizedEditableTask(scope, task.id) } returns task
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 3
            every {
                blueprintTaskRepository.findByBlueprintStepIdAndPositionBetween(step.id, 0, 1)
            } returns mutableListOf(sibling)
            every { blueprintTaskRepository.save(any()) } answers { firstArg() }

            val result =
                service.updateBlueprintTaskById(
                    scope,
                    task.id,
                    UpdateBlueprintTaskRequest(revision = 1, position = 0, title = "Task", description = "Description"),
                )

            assertEquals(0, result.position)
            assertEquals(1, sibling.position)
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableTask(scope, task.id) }
            verify(exactly = 1) { blueprintTaskRepository.save(any()) }
        }

        @Test
        fun `rejects stale revision without saving`() {
            val task = makeTask(position = 0, revision = 4, title = "Old", description = "Old")
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintTaskById(
                    BlueprintScope.Global,
                    task.id,
                    UpdateBlueprintTaskRequest(revision = 3, position = 0, title = "New", description = "Details"),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.save(any()) }
        }

        @Test
        fun `rejects a position outside the contiguous range`() {
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 0, revision = 1, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintTaskById(
                    BlueprintScope.Global,
                    task.id,
                    UpdateBlueprintTaskRequest(revision = 1, position = 1, title = "Task", description = "Description"),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.save(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val taskId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, taskId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintTaskById(
                    BlueprintScope.Global,
                    taskId,
                    UpdateBlueprintTaskRequest(revision = 0, position = 0, title = "Task", description = "Description"),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.save(any()) }
        }
    }

    @Nested
    inner class UpdateBlueprintTaskPositionById {
        @Test
        fun `moves the task and flushes shifted siblings together`() {
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 3, revision = 5, title = "Task", description = "Description")
            val firstSibling = makeTask(step, position = 1)
            val secondSibling = makeTask(step, position = 2)
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 4
            every {
                blueprintTaskRepository.findByBlueprintStepIdAndPositionBetween(step.id, 1, 2)
            } returns mutableListOf(firstSibling, secondSibling)
            every { blueprintTaskRepository.saveAllAndFlush(any<List<BlueprintTask>>()) } answers { firstArg() }

            val result =
                service.updateBlueprintTaskPositionById(
                    BlueprintScope.Global,
                    task.id,
                    UpdateBlueprintTaskPositionRequest(revision = 5, position = 1),
                )

            assertEquals(listOf(firstSibling.id, secondSibling.id, task.id), result.map { it.id })
            assertEquals(listOf(2, 3, 1), result.map { it.position })
            assertEquals(2, firstSibling.position)
            assertEquals(3, secondSibling.position)
            verify(exactly = 1) {
                blueprintTaskRepository.saveAllAndFlush(listOf(firstSibling, secondSibling, task))
            }
        }

        @Test
        fun `returns only the moved task when siblings do not overlap for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 0, revision = 1, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedEditableTask(scope, task.id) } returns task
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 3
            every {
                blueprintTaskRepository.saveAllAndFlush(any<List<BlueprintTask>>())
            } answers { firstArg() }

            val result =
                service.updateBlueprintTaskPositionById(
                    scope,
                    task.id,
                    UpdateBlueprintTaskPositionRequest(revision = 1, position = 0),
                )

            assertEquals(listOf(task.id), result.map { it.id })
            assertEquals(listOf(0), result.map { it.position })
            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableTask(scope, task.id) }
        }

        @Test
        fun `rejects stale revision without flushing`() {
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 0, revision = 5, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.updateBlueprintTaskPositionById(
                    BlueprintScope.Global,
                    task.id,
                    UpdateBlueprintTaskPositionRequest(revision = 4, position = 1),
                )
            }
            assertEquals(0, task.position)
            verify(exactly = 0) { blueprintTaskRepository.saveAllAndFlush(any<List<BlueprintTask>>()) }
        }

        @Test
        fun `rejects a position outside the contiguous range`() {
            val step = blueprintStepFixture()
            val task = makeTask(step, position = 0, revision = 1, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task
            every { blueprintTaskRepository.countByBlueprintStepId(step.id) } returns 1

            assertBlueprintStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintTaskPositionById(
                    BlueprintScope.Global,
                    task.id,
                    UpdateBlueprintTaskPositionRequest(revision = 1, position = 1),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.saveAllAndFlush(any<List<BlueprintTask>>()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val taskId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, taskId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintTaskPositionById(
                    BlueprintScope.Global,
                    taskId,
                    UpdateBlueprintTaskPositionRequest(revision = 0, position = 0),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.saveAllAndFlush(any<List<BlueprintTask>>()) }
        }
    }

    @Nested
    inner class DeleteBlueprintTaskById {
        @Test
        fun `deletes the task when the revision matches`() {
            val task = makeTask(position = 0, revision = 6, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task
            every { blueprintTaskRepository.delete(task) } just runs

            service.deleteBlueprintTaskById(
                BlueprintScope.Global,
                task.id,
                DeleteBlueprintTaskRequest(revision = 6),
            )

            verify(exactly = 1) { blueprintTaskRepository.delete(task) }
        }

        @Test
        fun `deletes the task for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val task = makeTask(position = 1, revision = 6, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedEditableTask(scope, task.id) } returns task
            every { blueprintTaskRepository.delete(task) } just runs

            service.deleteBlueprintTaskById(scope, task.id, DeleteBlueprintTaskRequest(revision = 6))

            verify(exactly = 1) { blueprintAccessService.getAuthorizedEditableTask(scope, task.id) }
            verify(exactly = 1) { blueprintTaskRepository.delete(task) }
        }

        @Test
        fun `rejects stale revision without deleting`() {
            val task = makeTask(position = 0, revision = 6, title = "Task", description = "Description")
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, task.id) } returns task

            assertBlueprintStatus(HttpStatus.CONFLICT) {
                service.deleteBlueprintTaskById(
                    BlueprintScope.Global,
                    task.id,
                    DeleteBlueprintTaskRequest(revision = 5),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.delete(any()) }
        }

        @Test
        fun `propagates not found from the access service`() {
            val taskId = UUID.randomUUID()
            every { blueprintAccessService.getAuthorizedEditableTask(BlueprintScope.Global, taskId) } throws
                ResponseStatusException(HttpStatus.NOT_FOUND)

            assertBlueprintStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintTaskById(
                    BlueprintScope.Global,
                    taskId,
                    DeleteBlueprintTaskRequest(revision = 0),
                )
            }
            verify(exactly = 0) { blueprintTaskRepository.delete(any()) }
        }
    }
}
