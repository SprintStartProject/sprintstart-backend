package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.CreateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.DeleteBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlueprintStepServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val blueprintStepRepository: BlueprintStepRepository = mockk()
    private val entityManager: EntityManager = mockk()
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService = mockk()
    private val service =
        BlueprintStepService(
            blueprintAccessService,
            blueprintStepRepository,
            entityManager,
            blueprintSubGraphNodeService,
        )

    private fun makeStep(
        phase: BlueprintPhase = blueprintPhaseFixture(),
        position: Int = 0,
        revision: Long = 0,
    ): BlueprintStep {
        return blueprintStepFixture(phase).also {
            it.position = position
            it.revision = revision
        }
    }

    private fun allowFlush() {
        every { entityManager.flush() } just runs
    }

    private fun assertStatus(
        expectedStatus: HttpStatus,
        action: () -> Unit,
    ): ResponseStatusException {
        val exception = assertThrows<ResponseStatusException>(action)
        assertEquals(expectedStatus, exception.statusCode)
        return exception
    }

    @Nested
    inner class GetBlueprintStepForPhase {
        @Test
        fun `returns mapped steps for global scope`() {
            val phase = blueprintPhaseFixture()
            val step = makeStep(phase, position = 0, revision = 2)
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(phase.id)
            } returns mutableListOf(step)

            val result = service.getBlueprintStepForPhase(BlueprintScope.Global, phase.id)

            assertEquals(1, result.size)
            val response = result.single()
            assertEquals(step.id, response.id)
            assertEquals(phase.id, response.blueprintPhaseId)
            assertEquals(2L, response.revision)
            assertEquals(0, response.position)
            assertEquals("Step", response.title)
            assertEquals(StepType.TASK, response.type)
            assertEquals(emptySet(), response.blockerIds)
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            val step = makeStep(phase)
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(projectId, phase.id)
            } returns mutableListOf(step)

            val result = service.getBlueprintStepForPhase(BlueprintScope.Project(projectId), phase.id)

            assertEquals(listOf(step.id), result.map { it.id })
        }
    }

    @Nested
    inner class GetBlueprintStepById {
        @Test
        fun `returns mapped step for global scope`() {
            val step = makeStep(revision = 3)
            every {
                blueprintAccessService.getAuthorizedStep(BlueprintScope.Global, step.id)
            } returns step

            val result = service.getBlueprintStepById(BlueprintScope.Global, step.id)

            assertEquals(step.id, result.id)
            assertEquals(step.blueprintPhase.id, result.blueprintPhaseId)
            assertEquals(3L, result.revision)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedStep(BlueprintScope.Global, step.id)
            }
        }

        @Test
        fun `returns mapped step for project scope`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            val step = makeStep(phase)
            every {
                blueprintAccessService.getAuthorizedStep(BlueprintScope.Project(projectId), step.id)
            } returns step

            val result = service.getBlueprintStepById(BlueprintScope.Project(projectId), step.id)

            assertEquals(step.id, result.id)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedStep(BlueprintScope.Project(projectId), step.id)
            }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val stepId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedStep(BlueprintScope.Global, stepId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.getBlueprintStepById(BlueprintScope.Global, stepId)
            }
        }
    }

    @Nested
    inner class CreateBlueprintStepForPhase {
        @Test
        fun `creates step at requested position and shifts siblings right for global scope`() {
            val phase = blueprintPhaseFixture()
            val sibling = makeStep(phase, position = 1)
            val request =
                CreateBlueprintStepRequest(
                    position = 1,
                    title = "New Step",
                    description = "Description",
                    type = StepType.TASK,
                    estimatedMinutes = 30,
                    expectedOutcome = "Outcome",
                    graphX = 4.0,
                    graphY = -2.5,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 2L
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(phase.id, 1)
            } returns mutableListOf(sibling)
            every { blueprintStepRepository.save(any()) } answers { firstArg() }

            val result = service.createBlueprintStepForPhase(BlueprintScope.Global, phase.id, request)

            assertEquals(phase.id, result.blueprintPhaseId)
            assertEquals(1L, result.revision)
            assertEquals(1, result.position)
            assertEquals("New Step", result.title)
            assertEquals("Description", result.description)
            assertEquals(StepType.TASK, result.type)
            assertEquals(false, result.aiAssisted)
            assertEquals(30, result.estimatedMinutes)
            assertEquals("Outcome", result.expectedOutcome)
            assertEquals(4.0, result.graphX)
            assertEquals(-2.5, result.graphY)
            assertEquals(2, sibling.position)
            verify(exactly = 1) { blueprintStepRepository.save(any()) }
        }

        @Test
        fun `creates step for project scope when the phase belongs to the project`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            val request =
                CreateBlueprintStepRequest(
                    position = 0,
                    title = "Step",
                    description = "Description",
                    type = StepType.TASK,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            } returns phase
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 0L
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(phase.id, 0)
            } returns mutableListOf()
            every { blueprintStepRepository.save(any()) } answers { firstArg() }

            val result = service.createBlueprintStepForPhase(BlueprintScope.Project(projectId), phase.id, request)

            assertEquals(0, result.position)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            }
        }

        @Test
        fun `rejects a position above the step count without saving`() {
            val phase = blueprintPhaseFixture()
            val request =
                CreateBlueprintStepRequest(
                    position = 3,
                    title = "Step",
                    description = "Description",
                    type = StepType.TASK,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintStepForPhase(BlueprintScope.Global, phase.id, request)
            }
            verify(exactly = 0) { blueprintStepRepository.save(any()) }
        }

        @Test
        fun `rejects a negative position without saving`() {
            val phase = blueprintPhaseFixture()
            val request =
                CreateBlueprintStepRequest(
                    position = -1,
                    title = "Step",
                    description = "Description",
                    type = StepType.TASK,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintStepForPhase(BlueprintScope.Global, phase.id, request)
            }
            verify(exactly = 0) { blueprintStepRepository.save(any()) }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val phaseId = UUID.randomUUID()
            val request =
                CreateBlueprintStepRequest(
                    position = 0,
                    title = "Step",
                    description = "Description",
                    type = StepType.TASK,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phaseId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.createBlueprintStepForPhase(BlueprintScope.Global, phaseId, request)
            }
        }

        @Test
        fun `propagates conflict when the owning path is not a draft`() {
            val phaseId = UUID.randomUUID()
            val request =
                CreateBlueprintStepRequest(
                    position = 0,
                    title = "Step",
                    description = "Description",
                    type = StepType.TASK,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phaseId)
            } throws ResponseStatusException(HttpStatus.CONFLICT)

            assertStatus(HttpStatus.CONFLICT) {
                service.createBlueprintStepForPhase(BlueprintScope.Global, phaseId, request)
            }
        }
    }

    @Nested
    inner class UpdateBlueprintStepById {
        @Test
        fun `updates fields and moves toward the end for global scope`() {
            val phase = blueprintPhaseFixture()
            val step = makeStep(phase, position = 1, revision = 2)
            val sibling = makeStep(phase, position = 2)
            val request =
                UpdateBlueprintStepRequest(
                    revision = 2,
                    position = 2,
                    title = "Renamed",
                    description = "New description",
                    type = StepType.TASK,
                    aiAssisted = true,
                    estimatedMinutes = 45,
                    expectedOutcome = "New outcome",
                )
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 3L
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseIdAndPositionBetween(phase.id, 2, 2)
            } returns mutableListOf(sibling)
            every { blueprintStepRepository.save(any()) } answers { firstArg() }

            val result = service.updateBlueprintStepById(BlueprintScope.Global, step.id, request)

            assertEquals(step.id, result.id)
            assertEquals(2L, result.revision)
            assertEquals(2, result.position)
            assertEquals("Renamed", result.title)
            assertEquals("New description", result.description)
            assertEquals(true, result.aiAssisted)
            assertEquals(45, result.estimatedMinutes)
            assertEquals("New outcome", result.expectedOutcome)
            assertEquals(1, sibling.position)
            verify(exactly = 1) { blueprintStepRepository.save(any()) }
        }

        @Test
        fun `moves toward the front for project scope`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            val step = makeStep(phase, position = 2, revision = 1)
            val sibling = makeStep(phase, position = 0)
            val request =
                UpdateBlueprintStepRequest(
                    revision = 1,
                    position = 0,
                    title = "Renamed",
                    description = "Description",
                    type = StepType.TASK,
                    aiAssisted = false,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                )
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Project(projectId), step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 3L
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseIdAndPositionBetween(phase.id, 0, 1)
            } returns mutableListOf(sibling)
            every { blueprintStepRepository.save(any()) } answers { firstArg() }

            val result = service.updateBlueprintStepById(BlueprintScope.Project(projectId), step.id, request)

            assertEquals(0, result.position)
            assertEquals(1, sibling.position)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Project(projectId), step.id)
            }
        }

        @Test
        fun `keeps position without querying siblings when the position is unchanged`() {
            val phase = blueprintPhaseFixture()
            val step = makeStep(phase, position = 1, revision = 2)
            val request =
                UpdateBlueprintStepRequest(
                    revision = 2,
                    position = 1,
                    title = "Renamed",
                    description = "Description",
                    type = StepType.TASK,
                    aiAssisted = false,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                )
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 2L
            every { blueprintStepRepository.save(any()) } answers { firstArg() }

            val result = service.updateBlueprintStepById(BlueprintScope.Global, step.id, request)

            assertEquals(1, result.position)
            verify(exactly = 0) {
                blueprintStepRepository.findAllByBlueprintPhaseIdAndPositionBetween(any(), any(), any())
            }
        }

        @Test
        fun `rejects stale revision without shifting or saving`() {
            val step = makeStep(position = 1, revision = 5)
            val request =
                UpdateBlueprintStepRequest(
                    revision = 4,
                    position = 1,
                    title = "Renamed",
                    description = "Description",
                    type = StepType.TASK,
                    aiAssisted = false,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                )
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step

            val exception =
                assertStatus(HttpStatus.CONFLICT) {
                    service.updateBlueprintStepById(BlueprintScope.Global, step.id, request)
                }

            assertTrue(exception.reason.orEmpty().contains("reload"))
            assertEquals(1, step.position)
            verify(exactly = 0) { blueprintStepRepository.save(any()) }
        }

        @Test
        fun `rejects a position equal to the step count without saving`() {
            val phase = blueprintPhaseFixture()
            val step = makeStep(phase, position = 0, revision = 2)
            val request =
                UpdateBlueprintStepRequest(
                    revision = 2,
                    position = 2,
                    title = "Renamed",
                    description = "Description",
                    type = StepType.TASK,
                    aiAssisted = false,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                )
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintStepById(BlueprintScope.Global, step.id, request)
            }
            assertEquals(0, step.position)
            verify(exactly = 0) { blueprintStepRepository.save(any()) }
        }

        @Test
        fun `rejects a negative position without saving`() {
            val phase = blueprintPhaseFixture()
            val step = makeStep(phase, position = 0, revision = 2)
            val request =
                UpdateBlueprintStepRequest(
                    revision = 2,
                    position = -1,
                    title = "Renamed",
                    description = "Description",
                    type = StepType.TASK,
                    aiAssisted = false,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                )
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintStepById(BlueprintScope.Global, step.id, request)
            }
            verify(exactly = 0) { blueprintStepRepository.save(any()) }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val stepId = UUID.randomUUID()
            val request =
                UpdateBlueprintStepRequest(
                    revision = 0,
                    position = 0,
                    title = "Renamed",
                    description = "Description",
                    type = StepType.TASK,
                    aiAssisted = false,
                    estimatedMinutes = 10,
                    expectedOutcome = "Outcome",
                )
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, stepId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintStepById(BlueprintScope.Global, stepId, request)
            }
        }
    }

    @Nested
    inner class UpdateBlueprintStepPositionById {
        @Test
        fun `reorders siblings and flushes the moved steps for global scope`() {
            val phase = blueprintPhaseFixture()
            val step = makeStep(phase, position = 0, revision = 3)
            val firstSibling = makeStep(phase, position = 1, revision = 7)
            val secondSibling = makeStep(phase, position = 2, revision = 9)
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 4L
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseIdAndPositionBetween(phase.id, 1, 2)
            } returns mutableListOf(firstSibling, secondSibling)
            every { blueprintStepRepository.saveAllAndFlush(any<Iterable<BlueprintStep>>()) } answers { firstArg() }

            val result =
                service.updateBlueprintStepPositionById(
                    BlueprintScope.Global,
                    step.id,
                    UpdateBlueprintStepPositionRequest(revision = 3, position = 2),
                )

            assertEquals(listOf(firstSibling.id, secondSibling.id, step.id), result.map { it.id })
            assertEquals(listOf(0, 1, 2), result.map { it.position })
            assertEquals(listOf(7L, 9L, 3L), result.map { it.revision })
            verify(exactly = 1) { blueprintStepRepository.saveAllAndFlush(any<Iterable<BlueprintStep>>()) }
        }

        @Test
        fun `moves toward the front for project scope`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            val step = makeStep(phase, position = 2, revision = 1)
            val sibling = makeStep(phase, position = 0, revision = 2)
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Project(projectId), step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 3L
            every {
                blueprintStepRepository
                    .findAllByBlueprintPhaseIdAndPositionBetween(phase.id, 0, 1)
            } returns mutableListOf(sibling)
            every { blueprintStepRepository.saveAllAndFlush(any<Iterable<BlueprintStep>>()) } answers { firstArg() }

            val result =
                service.updateBlueprintStepPositionById(
                    BlueprintScope.Project(projectId),
                    step.id,
                    UpdateBlueprintStepPositionRequest(revision = 1, position = 0),
                )

            assertEquals(listOf(sibling.id, step.id), result.map { it.id })
            assertEquals(listOf(1, 0), result.map { it.position })
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Project(projectId), step.id)
            }
        }

        @Test
        fun `rejects stale revision without saving`() {
            val step = makeStep(position = 0, revision = 4)
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step

            val exception =
                assertStatus(HttpStatus.CONFLICT) {
                    service.updateBlueprintStepPositionById(
                        BlueprintScope.Global,
                        step.id,
                        UpdateBlueprintStepPositionRequest(revision = 3, position = 0),
                    )
                }

            assertTrue(exception.reason.orEmpty().contains("reload"))
            assertEquals(0, step.position)
            verify(exactly = 0) { blueprintStepRepository.saveAllAndFlush(any<Iterable<BlueprintStep>>()) }
        }

        @Test
        fun `rejects an out of range position without saving`() {
            val phase = blueprintPhaseFixture()
            val step = makeStep(phase, position = 0, revision = 3)
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step
            every { blueprintStepRepository.countByBlueprintPhaseId(phase.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintStepPositionById(
                    BlueprintScope.Global,
                    step.id,
                    UpdateBlueprintStepPositionRequest(revision = 3, position = 2),
                )
            }
            verify(exactly = 0) { blueprintStepRepository.saveAllAndFlush(any<Iterable<BlueprintStep>>()) }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val stepId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, stepId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintStepPositionById(
                    BlueprintScope.Global,
                    stepId,
                    UpdateBlueprintStepPositionRequest(revision = 0, position = 0),
                )
            }
        }
    }

    @Nested
    inner class DeleteBlueprintStepById {
        @Test
        fun `removes sub graph connections before deleting and flushes for global scope`() {
            val step = makeStep(revision = 4)
            val dependant = makeStep()
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step
            every {
                blueprintSubGraphNodeService.removeAllConnections(step)
            } returns mutableListOf(step, dependant)
            every { blueprintStepRepository.delete(step) } just runs
            allowFlush()

            val result =
                service.deleteBlueprintStepById(
                    BlueprintScope.Global,
                    step.id,
                    DeleteBlueprintStepRequest(revision = 4),
                )

            assertEquals(listOf(step.id, dependant.id), result.updatedSteps.map { it.id })
            assertEquals(listOf(5L, 1L), result.updatedSteps.map { it.revision })
            verify(exactly = 1) { blueprintSubGraphNodeService.removeAllConnections(step) }
            verify(exactly = 1) { blueprintStepRepository.delete(step) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `deletes the step for project scope`() {
            val projectId = UUID.randomUUID()
            val phase = blueprintPhaseFixture(blueprintPathFixture(projectId))
            val step = makeStep(phase, revision = 4)
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Project(projectId), step.id)
            } returns step
            every { blueprintSubGraphNodeService.removeAllConnections(step) } returns mutableListOf(step)
            every { blueprintStepRepository.delete(step) } just runs
            allowFlush()

            val result =
                service.deleteBlueprintStepById(
                    BlueprintScope.Project(projectId),
                    step.id,
                    DeleteBlueprintStepRequest(revision = 4),
                )

            assertEquals(listOf(step.id), result.updatedSteps.map { it.id })
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Project(projectId), step.id)
            }
        }

        @Test
        fun `rejects stale revision without deleting or flushing`() {
            val step = makeStep(revision = 4)
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, step.id)
            } returns step

            assertStatus(HttpStatus.CONFLICT) {
                service.deleteBlueprintStepById(
                    BlueprintScope.Global,
                    step.id,
                    DeleteBlueprintStepRequest(revision = 3),
                )
            }
            verify(exactly = 0) { blueprintStepRepository.delete(any()) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val stepId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditableStep(BlueprintScope.Global, stepId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintStepById(
                    BlueprintScope.Global,
                    stepId,
                    DeleteBlueprintStepRequest(revision = 0),
                )
            }
        }
    }
}
