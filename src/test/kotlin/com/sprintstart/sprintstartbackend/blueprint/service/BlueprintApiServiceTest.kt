package com.sprintstart.sprintstartbackend.blueprint.service

import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.blueprint.repository.BlueprintPathRepository
import com.sprintstart.sprintstartbackend.shared.ids.BlueprintId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlueprintApiServiceTest {
    private val repository: BlueprintPathRepository = mockk()
    private val service = BlueprintApiService(repository)
    private val projectId = UUID.randomUUID()

    private fun blueprint(owner: UUID? = projectId): BlueprintPath {
        val path = BlueprintPath(blueprintKey = UUID.randomUUID(), title = "Backend", projectId = owner)
        val first = BlueprintPhase(
            blueprintPath = path,
            position = 0,
            title = "Setup",
            description = null,
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
        )
        val second = BlueprintPhase(
            blueprintPath = path,
            position = 1,
            title = "Codebase",
            description = "Read the code",
            aiPrompt = "Explain the modules",
            type = BlueprintPhaseType.AI_ENHANCED,
        )
        second.blockedBy += first
        path.blueprintPhases += listOf(first, second)
        return path
    }

    @Test
    fun `the owning project of a blueprint is its project id, and unknown ones have none`() {
        val owned = blueprint()
        val missing = UUID.randomUUID()
        every { repository.findById(owned.id) } returns Optional.of(owned)
        every { repository.findById(missing) } returns Optional.empty()

        assertEquals(ProjectId(projectId), service.projectIdOf(BlueprintId(owned.id)))
        assertNull(service.projectIdOf(BlueprintId(missing)))
    }

    @Test
    fun `only active blueprints of the project are counted and snapshotted`() {
        val active = blueprint()
        every { repository.countByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE) } returns 1
        every { repository.findAllByProjectIdAndStatus(projectId, BlueprintStatus.ACTIVE) } returns listOf(active)

        assertEquals(1, service.activeBlueprintCount(ProjectId(projectId)))
        assertEquals(listOf(active.id), service.activeBlueprints(ProjectId(projectId)).map { it.id })
    }

    @Test
    fun `a snapshot copies the phases and carries blockers as ids`() {
        val path = blueprint()
        every { repository.findById(path.id) } returns Optional.of(path)

        val snapshot = service.snapshot(BlueprintId(path.id))!!

        assertEquals(projectId, snapshot.projectId)
        val (first, second) = snapshot.phases
        assertEquals(listOf("Setup", "Codebase"), snapshot.phases.map { it.title })
        assertEquals("Explain the modules", second.aiPrompt)
        assertEquals(BlueprintPhaseType.AI_ENHANCED, second.type)
        assertEquals(setOf(first.id), second.blockedBy)
        assertEquals(emptySet(), first.blockedBy)
    }

    @Test
    fun `an unknown blueprint has no snapshot`() {
        val missing = UUID.randomUUID()
        every { repository.findById(missing) } returns Optional.empty()

        assertNull(service.snapshot(BlueprintId(missing)))
    }
}
