package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.AddBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.UpdateBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlueprintGraphNodeServiceTest {
    private val blueprintPhaseRepository: BlueprintPhaseRepository = mockk()
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val entityManager: EntityManager = mockk()
    private val service =
        BlueprintGraphNodeService(
            blueprintPhaseRepository,
            blueprintAccessService,
            entityManager,
        )

    private fun makePhase(
        path: BlueprintPath = blueprintPathFixture(),
        title: String = "Phase",
        revision: Long = 0,
        graphX: Double? = null,
        graphY: Double? = null,
    ): BlueprintPhase {
        return blueprintPhaseFixture(path).also {
            it.title = title
            it.revision = revision
            it.graphX = graphX
            it.graphY = graphY
        }
    }

    private fun allowFlush() {
        every { entityManager.flush() } just runs
    }

    private fun allowLock(phase: BlueprintPhase) {
        every { entityManager.lock(phase, LockModeType.OPTIMISTIC_FORCE_INCREMENT) } just runs
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
    inner class GetGraph {
        @Test
        fun `returns mapped nodes with and without positions for global scope`() {
            val path = blueprintPathFixture()
            val blocker = makePhase(path = path, title = "Blocker")
            val positioned = makePhase(path = path, title = "Positioned", revision = 3, graphX = 12.5, graphY = 8.0)
            positioned.blockedBy.add(blocker)
            val unpositioned = makePhase(path = path, title = "Unpositioned")
            every {
                blueprintPhaseRepository.findAllByBlueprintPathProjectIdIsNullAndBlueprintPathId(path.id)
            } returns mutableListOf(positioned, unpositioned)

            val result = service.getGraph(BlueprintScope.Global, path.id)

            assertEquals(listOf(positioned.id, unpositioned.id), result.nodes.map { it.id })
            val positionedNode = result.nodes[0]
            assertEquals(3, positionedNode.revision)
            assertEquals("Positioned", positionedNode.title)
            assertEquals(path.id, positionedNode.blueprintPathId)
            assertEquals(12.5, positionedNode.graphX)
            assertEquals(8.0, positionedNode.graphY)
            assertEquals(setOf(blocker.id), positionedNode.blockerIds)
            val unpositionedNode = result.nodes[1]
            assertNull(unpositionedNode.graphX)
            assertNull(unpositionedNode.graphY)
            assertTrue(unpositionedNode.blockerIds.isEmpty())
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val path = blueprintPathFixture(projectId)
            val phase = makePhase(path)
            every {
                blueprintPhaseRepository.findAllByBlueprintPathProjectIdAndBlueprintPathId(projectId, path.id)
            } returns mutableListOf(phase)

            val result = service.getGraph(BlueprintScope.Project(projectId), path.id)

            assertEquals(listOf(phase.id), result.nodes.map { it.id })
        }
    }

    @Nested
    inner class AddGraphNodeBlocker {
        @Test
        fun `adds blocker and returns predicted next revision for global scope`() {
            val node = makePhase(revision = 4)
            val blocker = makePhase()
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, blocker.id) } returns
                blocker
            allowLock(node)
            allowFlush()

            val result =
                service.addGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    blocker.id,
                    AddBlueprintGraphNodeBlockerRequest(revision = 4),
                )

            assertEquals(5, result.revision)
            assertEquals(setOf(blocker.id), result.blockerIds)
            assertTrue(blocker in node.blockedBy)
            verify(exactly = 1) { entityManager.lock(node, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `adds blocker and returns predicted next revision for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val node = makePhase(revision = 2)
            val blocker = makePhase()
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, node.id) } returns node
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, blocker.id) } returns blocker
            allowLock(node)
            allowFlush()

            val result =
                service.addGraphNodeBlocker(
                    scope,
                    node.id,
                    blocker.id,
                    AddBlueprintGraphNodeBlockerRequest(revision = 2),
                )

            assertEquals(3, result.revision)
            assertEquals(setOf(blocker.id), result.blockerIds)
            assertTrue(blocker in node.blockedBy)
            verify(exactly = 1) { entityManager.lock(node, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision`() {
            val node = makePhase(revision = 2)
            val blocker = makePhase()
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, blocker.id) } returns
                blocker

            val exception =
                assertStatus(HttpStatus.CONFLICT) {
                    service.addGraphNodeBlocker(
                        BlueprintScope.Global,
                        node.id,
                        blocker.id,
                        AddBlueprintGraphNodeBlockerRequest(revision = 1),
                    )
                }

            assertTrue(exception.reason.orEmpty().contains("reload"))
            assertTrue(node.blockedBy.isEmpty())
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects an existing blocker`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val node = makePhase(revision = 2)
            val blocker = makePhase()
            node.blockedBy.add(blocker)
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, node.id) } returns node
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, blocker.id) } returns blocker

            assertStatus(HttpStatus.FORBIDDEN) {
                service.addGraphNodeBlocker(
                    scope,
                    node.id,
                    blocker.id,
                    AddBlueprintGraphNodeBlockerRequest(revision = 2),
                )
            }
            assertEquals(setOf(blocker.id), node.blockedBy.map { it.id }.toSet())
            verify(exactly = 0) { entityManager.flush() }
        }

        @Test
        fun `rejects a self blocker as cyclic`() {
            val node = makePhase(revision = 2)
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.addGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    node.id,
                    AddBlueprintGraphNodeBlockerRequest(revision = 2),
                )
            }
            assertTrue(node.blockedBy.isEmpty())
        }

        @Test
        fun `rejects a transitive blocker cycle`() {
            val node = makePhase(revision = 2)
            val blocker = makePhase()
            val intermediate = makePhase()
            blocker.blockedBy.add(intermediate)
            intermediate.blockedBy.add(node)
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, blocker.id) } returns
                blocker

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.addGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    blocker.id,
                    AddBlueprintGraphNodeBlockerRequest(revision = 2),
                )
            }
            assertTrue(node.blockedBy.isEmpty())
        }
    }

    @Nested
    inner class UpdateGraphNodePositionById {
        @Test
        fun `updates both coordinates and flushes the persistence context`() {
            val node = makePhase(revision = 6)
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node
            allowFlush()

            val result =
                service.updateGraphNodePositionById(
                    BlueprintScope.Global,
                    node.id,
                    UpdateBlueprintGraphNodePositionRequest(revision = 6, graphX = 21.0, graphY = -4.5),
                )

            assertEquals(6, result.revision)
            assertEquals(21.0, result.graphX)
            assertEquals(-4.5, result.graphY)
            assertEquals(21.0, node.graphX)
            assertEquals(-4.5, node.graphY)
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `updates both coordinates for project scope`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val node = makePhase(revision = 1)
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, node.id) } returns node
            allowFlush()

            val result =
                service.updateGraphNodePositionById(
                    scope,
                    node.id,
                    UpdateBlueprintGraphNodePositionRequest(revision = 1, graphX = 0.0, graphY = 10.0),
                )

            assertEquals(1, result.revision)
            assertEquals(0.0, result.graphX)
            assertEquals(10.0, result.graphY)
            assertEquals(0.0, node.graphX)
            assertEquals(10.0, node.graphY)
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without changing coordinates or flushing`() {
            val node = makePhase(revision = 6, graphX = 1.0, graphY = 2.0)
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node

            assertStatus(HttpStatus.CONFLICT) {
                service.updateGraphNodePositionById(
                    BlueprintScope.Global,
                    node.id,
                    UpdateBlueprintGraphNodePositionRequest(revision = 5, graphX = 21.0, graphY = -4.5),
                )
            }
            assertEquals(1.0, node.graphX)
            assertEquals(2.0, node.graphY)
            verify(exactly = 0) { entityManager.flush() }
        }
    }

    @Nested
    inner class RemoveGraphNodeBlocker {
        @Test
        fun `removes blocker and returns remaining blocker ids`() {
            val node = makePhase(revision = 7)
            val removedBlocker = makePhase()
            val remainingBlocker = makePhase()
            node.blockedBy.addAll(setOf(removedBlocker, remainingBlocker))
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node
            every {
                blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, removedBlocker.id)
            } returns removedBlocker
            allowLock(node)
            allowFlush()

            val result =
                service.removeGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    removedBlocker.id,
                    RemoveBlueprintGraphNodeBlockerRequest(revision = 7),
                )

            assertEquals(8, result.revision)
            assertEquals(setOf(remainingBlocker.id), result.blockerIds)
            assertEquals(setOf(remainingBlocker.id), node.blockedBy.map { it.id }.toSet())
            verify(exactly = 1) { entityManager.lock(node, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without removing blocker`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val node = makePhase(revision = 7)
            val blocker = makePhase()
            node.blockedBy.add(blocker)
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, node.id) } returns node
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, blocker.id) } returns blocker

            assertStatus(HttpStatus.CONFLICT) {
                service.removeGraphNodeBlocker(
                    scope,
                    node.id,
                    blocker.id,
                    RemoveBlueprintGraphNodeBlockerRequest(revision = 6),
                )
            }
            assertTrue(blocker in node.blockedBy)
            verify(exactly = 0) { entityManager.flush() }
        }
    }

    @Nested
    inner class RemoveGraphNodePositionById {
        @Test
        fun `clears coordinates and all incoming and outgoing connections`() {
            val node = makePhase(revision = 10, graphX = 3.0, graphY = 4.0)
            val outgoingBlocker = makePhase()
            val dependant = makePhase(revision = 2)
            node.blockedBy.add(outgoingBlocker)
            dependant.blockedBy.add(node)
            every { blueprintAccessService.getAuthorizedEditablePhase(BlueprintScope.Global, node.id) } returns node
            every { blueprintPhaseRepository.findAllBlockedById(node.id) } returns mutableListOf(dependant)
            allowLock(node)
            allowLock(dependant)
            allowFlush()

            val result =
                service.removeGraphNodePositionById(
                    BlueprintScope.Global,
                    node.id,
                    RemoveBlueprintGraphNodePositionRequest(revision = 10),
                )

            assertNull(node.graphX)
            assertNull(node.graphY)
            assertTrue(node.blockedBy.isEmpty())
            assertTrue(dependant.blockedBy.isEmpty())
            assertEquals(listOf(node.id, dependant.id), result.changedNodes.map { it.id })
            assertEquals(listOf(11L, 3L), result.changedNodes.map { it.revision })
            verify { entityManager.lock(dependant, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without clearing node state`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val node = makePhase(revision = 10, graphX = 3.0, graphY = 4.0)
            val blocker = makePhase()
            node.blockedBy.add(blocker)
            every { blueprintAccessService.getAuthorizedEditablePhase(scope, node.id) } returns node

            assertStatus(HttpStatus.CONFLICT) {
                service.removeGraphNodePositionById(
                    scope,
                    node.id,
                    RemoveBlueprintGraphNodePositionRequest(revision = 9),
                )
            }
            assertEquals(3.0, node.graphX)
            assertEquals(4.0, node.graphY)
            assertEquals(setOf(blocker.id), node.blockedBy.map { it.id }.toSet())
            verify(exactly = 0) { blueprintPhaseRepository.findAllBlockedById(any()) }
            verify(exactly = 0) { entityManager.flush() }
        }
    }

    @Nested
    inner class RemoveAllConnections {
        @Test
        fun `returns target first and disconnects each dependant`() {
            val node = makePhase()
            val blocker = makePhase()
            val firstDependant = makePhase()
            val secondDependant = makePhase()
            node.blockedBy.add(blocker)
            firstDependant.blockedBy.add(node)
            secondDependant.blockedBy.add(node)
            every { blueprintPhaseRepository.findAllBlockedById(node.id) } returns
                mutableListOf(firstDependant, secondDependant)
            allowLock(firstDependant)
            allowLock(secondDependant)

            val result = service.removeAllConnections(node)

            assertEquals(listOf(node, firstDependant, secondDependant), result)
            assertTrue(node.blockedBy.isEmpty())
            assertTrue(firstDependant.blockedBy.isEmpty())
            assertTrue(secondDependant.blockedBy.isEmpty())
            verify(exactly = 1) {
                entityManager.lock(firstDependant, LockModeType.OPTIMISTIC_FORCE_INCREMENT)
            }
            verify(exactly = 1) {
                entityManager.lock(secondDependant, LockModeType.OPTIMISTIC_FORCE_INCREMENT)
            }
        }
    }
}
