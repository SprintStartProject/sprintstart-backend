package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.DeleteBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhasePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
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

class BlueprintPhaseServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val blueprintPhaseRepository: BlueprintPhaseRepository = mockk()
    private val blueprintGraphNodeService: BlueprintGraphNodeService = mockk()
    private val entityManager: EntityManager = mockk()
    private val service =
        BlueprintPhaseService(
            blueprintAccessService,
            blueprintPhaseRepository,
            blueprintGraphNodeService,
            entityManager,
        )

    private fun makePhase(
        path: BlueprintPath = blueprintPathFixture(),
        position: Int = 0,
        revision: Long = 0,
    ): BlueprintPhase {
        return blueprintPhaseFixture(path).also {
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
    inner class GetBlueprintPhasesForPath {
        @Test
        fun `returns mapped phases for global scope`() {
            val path = blueprintPathFixture()
            val phase = makePhase(path, position = 0, revision = 2)
            every {
                blueprintPhaseRepository.findAllByBlueprintPathProjectIdIsNullAndBlueprintPathId(path.id)
            } returns mutableListOf(phase)

            val result = service.getBlueprintPhasesForPath(BlueprintScope.Global, path.id)

            assertEquals(1, result.size)
            val response = result.single()
            assertEquals(phase.id, response.id)
            assertEquals(path.id, response.blueprintPathId)
            assertEquals(2L, response.revision)
            assertEquals(0, response.position)
            assertEquals("Phase", response.title)
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            val phase = makePhase(path)
            every {
                blueprintPhaseRepository
                    .findAllByBlueprintPathProjectIdAndBlueprintPathId(projectId, path.id)
            } returns mutableListOf(phase)

            val result = service.getBlueprintPhasesForPath(BlueprintScope.Project(projectId), path.id)

            assertEquals(listOf(phase.id), result.map { it.id })
        }
    }

    @Nested
    inner class GetBlueprintPhaseById {
        @Test
        fun `returns mapped phase for global scope`() {
            val phase = makePhase(revision = 3)
            every {
                blueprintAccessService.getAuthorizedPhase(BlueprintScope.Global, phase.id)
            } returns phase

            val result = service.getBlueprintPhaseById(BlueprintScope.Global, phase.id)

            assertEquals(phase.id, result.id)
            assertEquals(phase.blueprintPath.id, result.blueprintPathId)
            assertEquals(3L, result.revision)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedPhase(BlueprintScope.Global, phase.id)
            }
        }

        @Test
        fun `returns mapped phase for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            val phase = makePhase(path)
            every {
                blueprintAccessService.getAuthorizedPhase(BlueprintScope.Project(projectId), phase.id)
            } returns phase

            val result = service.getBlueprintPhaseById(BlueprintScope.Project(projectId), phase.id)

            assertEquals(phase.id, result.id)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedPhase(BlueprintScope.Project(projectId), phase.id)
            }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val phaseId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedPhase(BlueprintScope.Global, phaseId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.getBlueprintPhaseById(BlueprintScope.Global, phaseId)
            }
        }
    }

    @Nested
    inner class CreateBlueprintPhaseForPath {
        @Test
        fun `creates phase at requested position and shifts siblings right for global scope`() {
            val path = blueprintPathFixture()
            val sibling = makePhase(path, position = 1)
            val request =
                CreateBlueprintPhaseRequest(
                    position = 1,
                    title = "New Phase",
                    description = "Description",
                    aiPrompt = "Prompt",
                    type = BlueprintPhaseType.FIXED,
                    graphX = 4.0,
                    graphY = -2.5,
                )
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, path.id)
            } returns path
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 2L
            every {
                blueprintPhaseRepository
                    .findAllByBlueprintPathIdAndPositionGreaterThanEqualOrderByPositionDesc(path.id, 1)
            } returns mutableListOf(sibling)
            every { blueprintPhaseRepository.save(any()) } answers { firstArg() }

            val result = service.createBlueprintPhaseForPath(BlueprintScope.Global, path.id, request)

            assertEquals(path.id, result.blueprintPathId)
            assertEquals(1L, result.revision)
            assertEquals(1, result.position)
            assertEquals("New Phase", result.title)
            assertEquals("Description", result.description)
            assertEquals("Prompt", result.aiPrompt)
            assertEquals(BlueprintPhaseType.FIXED, result.type)
            assertEquals(4.0, result.graphX)
            assertEquals(-2.5, result.graphY)
            assertEquals(2, sibling.position)
            verify(exactly = 1) { blueprintPhaseRepository.save(any()) }
        }

        @Test
        fun `creates phase for project scope when the path belongs to the project`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            val request =
                CreateBlueprintPhaseRequest(
                    position = 0,
                    title = "Phase",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Project(projectId), path.id)
            } returns path
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 0L
            every {
                blueprintPhaseRepository
                    .findAllByBlueprintPathIdAndPositionGreaterThanEqualOrderByPositionDesc(path.id, 0)
            } returns mutableListOf()
            every { blueprintPhaseRepository.save(any()) } answers { firstArg() }

            val result = service.createBlueprintPhaseForPath(BlueprintScope.Project(projectId), path.id, request)

            assertEquals(0, result.position)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Project(projectId), path.id)
            }
        }

        @Test
        fun `rejects a position above the phase count without saving`() {
            val path = blueprintPathFixture()
            val request =
                CreateBlueprintPhaseRequest(
                    position = 3,
                    title = "Phase",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, path.id)
            } returns path
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintPhaseForPath(BlueprintScope.Global, path.id, request)
            }
            verify(exactly = 0) { blueprintPhaseRepository.save(any()) }
        }

        @Test
        fun `rejects a negative position without saving`() {
            val path = blueprintPathFixture()
            val request =
                CreateBlueprintPhaseRequest(
                    position = -1,
                    title = "Phase",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, path.id)
            } returns path
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.createBlueprintPhaseForPath(BlueprintScope.Global, path.id, request)
            }
            verify(exactly = 0) { blueprintPhaseRepository.save(any()) }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val pathId = UUID.randomUUID()
            val request =
                CreateBlueprintPhaseRequest(
                    position = 0,
                    title = "Phase",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, pathId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.createBlueprintPhaseForPath(BlueprintScope.Global, pathId, request)
            }
        }

        @Test
        fun `propagates conflict when the path is not a draft`() {
            val pathId = UUID.randomUUID()
            val request =
                CreateBlueprintPhaseRequest(
                    position = 0,
                    title = "Phase",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                    graphX = null,
                    graphY = null,
                )
            every {
                blueprintAccessService.getAuthorizedDraftPath(BlueprintScope.Global, pathId)
            } throws ResponseStatusException(HttpStatus.CONFLICT)

            assertStatus(HttpStatus.CONFLICT) {
                service.createBlueprintPhaseForPath(BlueprintScope.Global, pathId, request)
            }
        }
    }

    @Nested
    inner class UpdateBlueprintPhaseById {
        @Test
        fun `updates fields and moves toward the end for global scope`() {
            val path = blueprintPathFixture()
            val phase = makePhase(path, position = 1, revision = 2)
            val sibling = makePhase(path, position = 2)
            val request =
                UpdateBlueprintPhaseRequest(
                    revision = 2,
                    position = 2,
                    title = "Renamed",
                    description = "New description",
                    aiPrompt = "New prompt",
                    type = BlueprintPhaseType.FIXED,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 3L
            every {
                blueprintPhaseRepository
                    .findAllByBlueprintPathIdAndPositionBetween(path.id, 2, 2)
            } returns mutableListOf(sibling)
            every { blueprintPhaseRepository.save(any()) } answers { firstArg() }

            val result = service.updateBlueprintPhaseById(BlueprintScope.Global, phase.id, request)

            assertEquals(phase.id, result.id)
            assertEquals(2L, result.revision)
            assertEquals(2, result.position)
            assertEquals("Renamed", result.title)
            assertEquals("New description", result.description)
            assertEquals("New prompt", result.aiPrompt)
            assertEquals(1, sibling.position)
            verify(exactly = 1) { blueprintPhaseRepository.save(any()) }
        }

        @Test
        fun `moves toward the front for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            val phase = makePhase(path, position = 2, revision = 1)
            val sibling = makePhase(path, position = 0)
            val request =
                UpdateBlueprintPhaseRequest(
                    revision = 1,
                    position = 0,
                    title = "Renamed",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 3L
            every {
                blueprintPhaseRepository
                    .findAllByBlueprintPathIdAndPositionBetween(path.id, 0, 1)
            } returns mutableListOf(sibling)
            every { blueprintPhaseRepository.save(any()) } answers { firstArg() }

            val result = service.updateBlueprintPhaseById(BlueprintScope.Project(projectId), phase.id, request)

            assertEquals(0, result.position)
            assertEquals(1, sibling.position)
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            }
        }

        @Test
        fun `keeps position without querying siblings when the position is unchanged`() {
            val path = blueprintPathFixture()
            val phase = makePhase(path, position = 1, revision = 2)
            val request =
                UpdateBlueprintPhaseRequest(
                    revision = 2,
                    position = 1,
                    title = "Renamed",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 2L
            every { blueprintPhaseRepository.save(any()) } answers { firstArg() }

            val result = service.updateBlueprintPhaseById(BlueprintScope.Global, phase.id, request)

            assertEquals(1, result.position)
            verify(exactly = 0) {
                blueprintPhaseRepository.findAllByBlueprintPathIdAndPositionBetween(any(), any(), any())
            }
        }

        @Test
        fun `rejects stale revision without shifting or saving`() {
            val phase = makePhase(position = 1, revision = 5)
            val request =
                UpdateBlueprintPhaseRequest(
                    revision = 4,
                    position = 1,
                    title = "Renamed",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            val exception =
                assertStatus(HttpStatus.CONFLICT) {
                    service.updateBlueprintPhaseById(BlueprintScope.Global, phase.id, request)
                }

            assertTrue(exception.reason.orEmpty().contains("reload"))
            assertEquals(1, phase.position)
            verify(exactly = 0) { blueprintPhaseRepository.save(any()) }
        }

        @Test
        fun `rejects a position equal to the phase count without saving`() {
            val path = blueprintPathFixture()
            val phase = makePhase(path, position = 0, revision = 2)
            val request =
                UpdateBlueprintPhaseRequest(
                    revision = 2,
                    position = 2,
                    title = "Renamed",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintPhaseById(BlueprintScope.Global, phase.id, request)
            }
            assertEquals(0, phase.position)
            verify(exactly = 0) { blueprintPhaseRepository.save(any()) }
        }

        @Test
        fun `rejects a negative position without saving`() {
            val path = blueprintPathFixture()
            val phase = makePhase(path, position = 0, revision = 2)
            val request =
                UpdateBlueprintPhaseRequest(
                    revision = 2,
                    position = -1,
                    title = "Renamed",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintPhaseById(BlueprintScope.Global, phase.id, request)
            }
            verify(exactly = 0) { blueprintPhaseRepository.save(any()) }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val phaseId = UUID.randomUUID()
            val request =
                UpdateBlueprintPhaseRequest(
                    revision = 0,
                    position = 0,
                    title = "Renamed",
                    description = null,
                    aiPrompt = null,
                    type = BlueprintPhaseType.FIXED,
                )
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phaseId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintPhaseById(BlueprintScope.Global, phaseId, request)
            }
        }
    }

    @Nested
    inner class UpdateBlueprintPhasePositionById {
        @Test
        fun `reorders siblings and flushes the moved phases for global scope`() {
            val path = blueprintPathFixture()
            val phase = makePhase(path, position = 0, revision = 3)
            val firstSibling = makePhase(path, position = 1, revision = 7)
            val secondSibling = makePhase(path, position = 2, revision = 9)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 4L
            every {
                blueprintPhaseRepository
                    .findAllByBlueprintPathIdAndPositionBetween(path.id, 1, 2)
            } returns mutableListOf(firstSibling, secondSibling)
            every { blueprintPhaseRepository.saveAllAndFlush(any<Iterable<BlueprintPhase>>()) } answers { firstArg() }

            val result =
                service.updateBlueprintPhasePositionById(
                    BlueprintScope.Global,
                    phase.id,
                    UpdateBlueprintPhasePositionRequest(revision = 3, position = 2),
                )

            assertEquals(listOf(firstSibling.id, secondSibling.id, phase.id), result.map { it.id })
            assertEquals(listOf(0, 1, 2), result.map { it.position })
            assertEquals(listOf(7L, 9L, 3L), result.map { it.revision })
            verify(exactly = 1) { blueprintPhaseRepository.saveAllAndFlush(any<Iterable<BlueprintPhase>>()) }
        }

        @Test
        fun `moves toward the front for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            val phase = makePhase(path, position = 2, revision = 1)
            val sibling = makePhase(path, position = 0, revision = 2)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 3L
            every {
                blueprintPhaseRepository
                    .findAllByBlueprintPathIdAndPositionBetween(path.id, 0, 1)
            } returns mutableListOf(sibling)
            every { blueprintPhaseRepository.saveAllAndFlush(any<Iterable<BlueprintPhase>>()) } answers { firstArg() }

            val result =
                service.updateBlueprintPhasePositionById(
                    BlueprintScope.Project(projectId),
                    phase.id,
                    UpdateBlueprintPhasePositionRequest(revision = 1, position = 0),
                )

            assertEquals(listOf(sibling.id, phase.id), result.map { it.id })
            assertEquals(listOf(1, 0), result.map { it.position })
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            }
        }

        @Test
        fun `rejects stale revision without saving`() {
            val phase = makePhase(position = 0, revision = 3)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            val exception =
                assertStatus(HttpStatus.CONFLICT) {
                    service.updateBlueprintPhasePositionById(
                        BlueprintScope.Global,
                        phase.id,
                        UpdateBlueprintPhasePositionRequest(revision = 2, position = 0),
                    )
                }

            assertTrue(exception.reason.orEmpty().contains("reload"))
            assertEquals(0, phase.position)
            verify(exactly = 0) { blueprintPhaseRepository.saveAllAndFlush(any<Iterable<BlueprintPhase>>()) }
        }

        @Test
        fun `rejects an out of range position without saving`() {
            val path = blueprintPathFixture()
            val phase = makePhase(path, position = 0, revision = 3)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintPhaseRepository.countByBlueprintPathId(path.id) } returns 2L

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.updateBlueprintPhasePositionById(
                    BlueprintScope.Global,
                    phase.id,
                    UpdateBlueprintPhasePositionRequest(revision = 3, position = 2),
                )
            }
            verify(exactly = 0) { blueprintPhaseRepository.saveAllAndFlush(any<Iterable<BlueprintPhase>>()) }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val phaseId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phaseId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.updateBlueprintPhasePositionById(
                    BlueprintScope.Global,
                    phaseId,
                    UpdateBlueprintPhasePositionRequest(revision = 0, position = 0),
                )
            }
        }
    }

    @Nested
    inner class DeleteBlueprintPhaseById {
        @Test
        fun `removes connections before deleting and flushes for global scope`() {
            val phase = makePhase(revision = 4)
            val dependant = makePhase()
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase
            every { blueprintGraphNodeService.removeAllConnections(phase) } returns mutableListOf(phase, dependant)
            every { blueprintPhaseRepository.delete(phase) } just runs
            allowFlush()

            val result =
                service.deleteBlueprintPhaseById(
                    BlueprintScope.Global,
                    phase.id,
                    DeleteBlueprintPhaseRequest(revision = 4),
                )

            assertEquals(listOf(phase.id, dependant.id), result.updatedPhases.map { it.id })
            assertEquals(listOf(5L, 1L), result.updatedPhases.map { it.revision })
            verify(exactly = 1) { blueprintGraphNodeService.removeAllConnections(phase) }
            verify(exactly = 1) { blueprintPhaseRepository.delete(phase) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `deletes the phase for project scope`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            val phase = makePhase(path, revision = 4)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            } returns phase
            every { blueprintGraphNodeService.removeAllConnections(phase) } returns mutableListOf(phase)
            every { blueprintPhaseRepository.delete(phase) } just runs
            allowFlush()

            val result =
                service.deleteBlueprintPhaseById(
                    BlueprintScope.Project(projectId),
                    phase.id,
                    DeleteBlueprintPhaseRequest(revision = 4),
                )

            assertEquals(listOf(phase.id), result.updatedPhases.map { it.id })
            verify(exactly = 1) {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Project(projectId), phase.id)
            }
        }

        @Test
        fun `rejects stale revision without deleting or flushing`() {
            val phase = makePhase(revision = 4)
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phase.id)
            } returns phase

            assertStatus(HttpStatus.CONFLICT) {
                service.deleteBlueprintPhaseById(
                    BlueprintScope.Global,
                    phase.id,
                    DeleteBlueprintPhaseRequest(revision = 3),
                )
            }
            verify(exactly = 0) { blueprintPhaseRepository.delete(any()) }
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `propagates 404 from the access service`() {
            val phaseId = UUID.randomUUID()
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, phaseId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertStatus(HttpStatus.NOT_FOUND) {
                service.deleteBlueprintPhaseById(
                    BlueprintScope.Global,
                    phaseId,
                    DeleteBlueprintPhaseRequest(revision = 0),
                )
            }
        }
    }
}
