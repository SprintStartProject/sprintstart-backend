package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintSubGraphNodeType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.AddBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.UpdateBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintSubGraphNodeRepository
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
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

class BlueprintSubGraphNodeServiceTest {
    private val blueprintAccessService: BlueprintAccessService = mockk()
    private val entityManager: EntityManager = mockk()
    private val blueprintSubGraphNodeRepository: BlueprintSubGraphNodeRepository = mockk()
    private val service =
        BlueprintSubGraphNodeService(
            blueprintAccessService,
            entityManager,
            blueprintSubGraphNodeRepository,
        )

    private fun makePath(projectId: UUID? = null): BlueprintPath {
        return BlueprintPath(
            blueprintKey = UUID.randomUUID(),
            projectId = projectId,
            title = "Blueprint",
            status = BlueprintStatus.DRAFT,
        )
    }

    private fun makePhase(path: BlueprintPath = makePath()): BlueprintPhase {
        return BlueprintPhase(
            blueprintPath = path,
            position = 0,
            title = "Phase",
            description = null,
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
        )
    }

    private fun makeStep(
        phase: BlueprintPhase = makePhase(),
        title: String = "Step",
        revision: Long = 0,
        graphX: Double? = null,
        graphY: Double? = null,
    ): BlueprintStep {
        return BlueprintStep(
            blueprintPhase = phase,
            title = title,
            graphX = graphX,
            graphY = graphY,
            position = 0,
            description = "Description",
            type = StepType.TASK,
            estimatedMinutes = 10,
            expectedOutcome = "Outcome",
        ).also { it.revision = revision }
    }

    private fun allowFlush() {
        every { entityManager.flush() } just runs
    }

    private fun allowLock(node: BlueprintStep) {
        every { entityManager.lock(node, LockModeType.OPTIMISTIC_FORCE_INCREMENT) } just runs
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
    inner class GetSubGraph {
        @Test
        fun `returns mapped nodes for global scope`() {
            val phase = makePhase()
            val blocker = makeStep(phase = phase, title = "Blocker")
            val node = makeStep(phase = phase, title = "Blocked", revision = 3, graphX = 12.5, graphY = 8.0)
            node.blockedBy.add(blocker)
            every {
                blueprintSubGraphNodeRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(phase.id)
            } returns mutableListOf(node)

            val result = service.getSubGraph(BlueprintScope.Global, phase.id)

            assertEquals(1, result.nodes.size)
            assertEquals(node.id, result.nodes.single().id)
            assertEquals(3, result.nodes.single().revision)
            assertEquals(BlueprintSubGraphNodeType.STEP, result.nodes.single().type)
            assertEquals(setOf(blocker.id), result.nodes.single().blockerIds)
        }

        @Test
        fun `restricts project scope lookup by project id`() {
            val projectId = UUID.randomUUID()
            val phase = makePhase(makePath(projectId))
            val node = makeStep(phase)
            every {
                blueprintSubGraphNodeRepository
                    .findByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(projectId, phase.id)
            } returns mutableListOf(node)

            val result = service.getSubGraph(BlueprintScope.Project(projectId), phase.id)

            assertEquals(listOf(node.id), result.nodes.map { it.id })
        }
    }

    @Nested
    inner class AddSubGraphNodeBlocker {
        @Test
        fun `adds blocker and returns predicted next revision`() {
            val scope = BlueprintScope.Project(UUID.randomUUID())
            val node = makeStep(revision = 4)
            val blocker = makeStep()
            every { blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, node.id) } returns node
            every { blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, blocker.id) } returns blocker
            allowLock(node)
            allowFlush()

            val result =
                service.addSubGraphNodeBlocker(
                    scope,
                    node.id,
                    blocker.id,
                    AddBlueprintSubGraphNodeBlockerRequest(revision = 4),
                )

            assertEquals(5, result.revision)
            assertEquals(setOf(blocker.id), result.blockerIds)
            assertTrue(blocker in node.blockedBy)
            verify(exactly = 1) { entityManager.lock(node, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision`() {
            val node = makeStep(revision = 2)
            val blocker = makeStep()
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, blocker.id)
            } returns blocker

            val exception =
                assertStatus(HttpStatus.CONFLICT) {
                    service.addSubGraphNodeBlocker(
                        BlueprintScope.Global,
                        node.id,
                        blocker.id,
                        AddBlueprintSubGraphNodeBlockerRequest(revision = 1),
                    )
                }

            assertTrue(exception.reason.orEmpty().contains("reload"))
            assertTrue(node.blockedBy.isEmpty())
        }

        @Test
        fun `rejects an existing blocker`() {
            val node = makeStep(revision = 2)
            val blocker = makeStep()
            node.blockedBy.add(blocker)
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, blocker.id)
            } returns blocker

            assertStatus(HttpStatus.FORBIDDEN) {
                service.addSubGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    blocker.id,
                    AddBlueprintSubGraphNodeBlockerRequest(revision = 2),
                )
            }
        }

        @Test
        fun `rejects a transitive blocker cycle`() {
            val node = makeStep(revision = 2)
            val blocker = makeStep()
            val intermediate = makeStep()
            blocker.blockedBy.add(intermediate)
            intermediate.blockedBy.add(node)
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, blocker.id)
            } returns blocker

            assertStatus(HttpStatus.BAD_REQUEST) {
                service.addSubGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    blocker.id,
                    AddBlueprintSubGraphNodeBlockerRequest(revision = 2),
                )
            }
            assertTrue(node.blockedBy.isEmpty())
        }
    }

    @Nested
    inner class UpdateSubGraphNodePositionById {
        @Test
        fun `updates both coordinates and flushes the persistence context`() {
            val node = makeStep(revision = 6)
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node
            allowFlush()

            val result =
                service.updateSubGraphNodePositionById(
                    BlueprintScope.Global,
                    node.id,
                    UpdateBlueprintSubGraphNodePositionRequest(revision = 6, graphX = 21.0, graphY = -4.5),
                )

            assertEquals(6, result.revision)
            assertEquals(21.0, result.graphX)
            assertEquals(-4.5, result.graphY)
            assertEquals(21.0, node.graphX)
            assertEquals(-4.5, node.graphY)
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without changing coordinates`() {
            val node = makeStep(revision = 6, graphX = 1.0, graphY = 2.0)
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node

            assertStatus(HttpStatus.CONFLICT) {
                service.updateSubGraphNodePositionById(
                    BlueprintScope.Global,
                    node.id,
                    UpdateBlueprintSubGraphNodePositionRequest(revision = 5, graphX = 21.0, graphY = -4.5),
                )
            }
            assertEquals(1.0, node.graphX)
            assertEquals(2.0, node.graphY)
        }
    }

    @Nested
    inner class RemoveSubGraphNodeBlocker {
        @Test
        fun `removes blocker and returns remaining blocker ids`() {
            val node = makeStep(revision = 7)
            val removedBlocker = makeStep()
            val remainingBlocker = makeStep()
            node.blockedBy.addAll(setOf(removedBlocker, remainingBlocker))
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, removedBlocker.id)
            } returns removedBlocker
            allowLock(node)
            allowFlush()

            val result =
                service.removeSubGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    removedBlocker.id,
                    RemoveBlueprintSubGraphNodeBlockerRequest(revision = 7),
                )

            assertEquals(8, result.revision)
            assertEquals(setOf(remainingBlocker.id), result.blockerIds)
            assertEquals(setOf(remainingBlocker.id), node.blockedBy.map { it.id }.toSet())
            verify(exactly = 1) { entityManager.lock(node, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without removing blocker`() {
            val node = makeStep(revision = 7)
            val blocker = makeStep()
            node.blockedBy.add(blocker)
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, blocker.id)
            } returns blocker

            assertStatus(HttpStatus.CONFLICT) {
                service.removeSubGraphNodeBlocker(
                    BlueprintScope.Global,
                    node.id,
                    blocker.id,
                    RemoveBlueprintSubGraphNodeBlockerRequest(revision = 6),
                )
            }
            assertTrue(blocker in node.blockedBy)
        }
    }

    @Nested
    inner class RemoveSubGraphNodePositionById {
        @Test
        fun `clears coordinates and all incoming and outgoing connections`() {
            val node = makeStep(revision = 10, graphX = 3.0, graphY = 4.0)
            val outgoingBlocker = makeStep()
            val dependant = makeStep(revision = 2)
            node.blockedBy.add(outgoingBlocker)
            dependant.blockedBy.add(node)
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node
            every { blueprintSubGraphNodeRepository.findAllByBlockedBy(node.id) } returns mutableListOf(dependant)
            allowLock(node)
            allowLock(dependant)
            allowFlush()

            val result =
                service.removeSubGraphNodePositionById(
                    BlueprintScope.Global,
                    node.id,
                    RemoveBlueprintSubGraphNodePositionRequest(revision = 10),
                )

            assertNull(node.graphX)
            assertNull(node.graphY)
            assertTrue(node.blockedBy.isEmpty())
            assertTrue(dependant.blockedBy.isEmpty())
            assertEquals(listOf(node.id, dependant.id), result.updatedNodes.map { it.id })
            assertEquals(listOf(11L, 3L), result.updatedNodes.map { it.revision })
            verify { entityManager.lock(dependant, LockModeType.OPTIMISTIC_FORCE_INCREMENT) }
            verify(exactly = 1) { entityManager.flush() }
        }

        @Test
        fun `rejects stale revision without clearing node state`() {
            val node = makeStep(revision = 10, graphX = 3.0, graphY = 4.0)
            val blocker = makeStep()
            node.blockedBy.add(blocker)
            every {
                blueprintAccessService.getAuthorizedEditableSubGraphNode(BlueprintScope.Global, node.id)
            } returns node

            assertStatus(HttpStatus.CONFLICT) {
                service.removeSubGraphNodePositionById(
                    BlueprintScope.Global,
                    node.id,
                    RemoveBlueprintSubGraphNodePositionRequest(revision = 9),
                )
            }
            assertEquals(3.0, node.graphX)
            assertEquals(4.0, node.graphY)
            assertEquals(setOf(blocker.id), node.blockedBy.map { it.id }.toSet())
        }
    }

    @Nested
    inner class RemoveAllConnections {
        @Test
        fun `returns target first and disconnects each dependant`() {
            val node = makeStep()
            val blocker = makeStep()
            val firstDependant = makeStep()
            val secondDependant = makeStep()
            node.blockedBy.add(blocker)
            firstDependant.blockedBy.add(node)
            secondDependant.blockedBy.add(node)
            every { blueprintSubGraphNodeRepository.findAllByBlockedBy(node.id) } returns
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
