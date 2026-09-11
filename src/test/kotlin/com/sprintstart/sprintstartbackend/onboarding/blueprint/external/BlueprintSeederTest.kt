package com.sprintstart.sprintstartbackend.onboarding.blueprint.external

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPathRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class BlueprintSeederTest {
    private val blueprintPathRepository: BlueprintPathRepository = mockk()

    private val seeder = BlueprintSeeder(blueprintPathRepository)

    @Test
    fun `does nothing when a global blueprint path already exists`() {
        val existing = BlueprintPath(blueprintKey = UUID.randomUUID(), title = "Existing Blueprint")
        every { blueprintPathRepository.findAllByProjectIdIsNull() } returns listOf(existing)

        seeder.seed()

        verify(exactly = 0) { blueprintPathRepository.save(any()) }
    }

    @Test
    fun `seeds the default global blueprint path with phases and blocker graph`() {
        every { blueprintPathRepository.findAllByProjectIdIsNull() } returns emptyList()
        val savedSlot = slot<BlueprintPath>()
        every { blueprintPathRepository.save(capture(savedSlot)) } answers { firstArg() }

        seeder.seed()

        verify(exactly = 1) { blueprintPathRepository.save(any()) }

        val saved = savedSlot.captured
        assertEquals(BlueprintStatus.ACTIVE, saved.status)
        assertNull(saved.projectId)
        assertEquals(0, saved.version)
        assertEquals("Seeded Blueprint", saved.title)

        assertEquals(BlueprintSeedData.phases.size, saved.blueprintPhases.size)
        saved.blueprintPhases.forEachIndexed { index, phase ->
            val seed = BlueprintSeedData.phases[index]
            assertEquals(saved, phase.blueprintPath)
            assertEquals(index, phase.position)
            assertEquals(seed.title, phase.title)
            assertEquals(seed.description, phase.description)
            assertEquals(seed.aiPrompt, phase.aiPrompt)
            assertEquals(BlueprintPhaseType.AI_ENHANCED, phase.type)
            assertEquals(seed.graphX, phase.graphX)
            assertEquals(seed.graphY, phase.graphY)
        }

        val blockerIndices = saved.blueprintPhases.map { phase ->
            phase.blockedBy.map { saved.blueprintPhases.indexOf(it) }.toSet()
        }
        val expectedBlockerIndices = listOf(
            emptySet(),
            setOf(0),
            setOf(0),
            setOf(0),
            setOf(0),
            setOf(0),
            setOf(0),
            setOf(0),
            setOf(0),
            setOf(6, 7),
            setOf(9),
            setOf(10),
            setOf(10),
            setOf(11, 12),
            setOf(13, 8),
            setOf(13, 8),
        )
        assertEquals(expectedBlockerIndices, blockerIndices)
    }
}
