package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.blueprint.external.enums.StepType
import com.sprintstart.sprintstartbackend.path.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.path.repository.OnboardingPathRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathStepReaderTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val reader = PathStepReader(onboardingPathRepository)

    private val userId = UUID.randomUUID()
    private lateinit var path: OnboardingPath

    @BeforeEach
    fun setUp() {
        path = OnboardingPath(userId = userId)
    }

    @Suppress("LongParameterList")
    private fun addStep(
        phase: OnboardingPhase,
        title: String,
        position: Int,
        id: UUID = UUID.randomUUID(),
    ): OnboardingStep {
        val step = OnboardingStep(
            id = id,
            phase = phase,
            position = position,
            title = title,
            description = "Desc",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Outcome",
            status = StepStatus.WAITING,
        )
        phase.steps.add(step)
        return step
    }

    private fun addPhase(
        position: Int,
        title: String,
        generationStatus: GenerationStatus = GenerationStatus.NOT_APPLICABLE,
    ): OnboardingPhase {
        val phase = OnboardingPhase(
            path = path,
            position = position,
            title = title,
            description = "Desc",
            generationStatus = generationStatus,
        )
        path.phases.add(phase)
        return phase
    }

    private fun stubPath() {
        every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.of(path)
    }

    @Test
    fun `steps come back in phase-then-step position order`() {
        val phaseTwo = addPhase(position = 1, title = "Phase Two")
        val phaseOne = addPhase(position = 0, title = "Phase One")
        addStep(phaseTwo, "Second phase, first step", position = 0)
        addStep(phaseOne, "First phase, second step", position = 1)
        addStep(phaseOne, "First phase, first step", position = 0)
        stubPath()

        val titles = reader.stepsFor(userId).map { it.title }

        assertEquals(
            listOf("First phase, first step", "First phase, second step", "Second phase, first step"),
            titles,
        )
    }

    @Test
    fun `a step carries what is shown of it, its tasks in order`() {
        val phase = addPhase(position = 0, title = "Phase One")
        val step = addStep(phase, "Set up your laptop", position = 0)
        step.aiAssisted = false
        step.tasks.add(OnboardingTask(step = step, position = 1, title = "Second", description = "Desc"))
        step.tasks.add(OnboardingTask(step = step, position = 0, title = "First", description = "Desc"))
        step.resources.add(
            OnboardingResource(step = step, title = "Guide", description = "Desc", url = "https://example.com"),
        )
        stubPath()

        val resolved = reader.stepsById(userId).getValue(step.id)

        assertEquals("Phase One", resolved.phaseTitle)
        assertEquals("Set up your laptop", resolved.title)
        assertEquals("Desc", resolved.description)
        assertEquals(StepStatus.WAITING, resolved.status)
        assertEquals(false, resolved.aiAssisted)
        assertEquals("Outcome", resolved.expectedOutcome)
        assertEquals(listOf("First", "Second"), resolved.tasks.map { it.title })
        assertEquals(listOf("Guide"), resolved.resources.map { it.title })
    }

    @Test
    fun `a user with no path has no steps`() {
        every { onboardingPathRepository.findOnboardingPathByUserId(userId) } returns Optional.empty()

        assertEquals(emptyList(), reader.stepsFor(userId))
    }

    @Test
    fun `phases hidden from the user are left out`() {
        addPhase(position = 0, title = "Visible").also { addStep(it, "A step", position = 0) }
        addPhase(position = 1, title = "Failed", generationStatus = GenerationStatus.FAILED)
            .also { addStep(it, "A hidden step", position = 0) }
        stubPath()

        val titles = reader.stepsFor(userId).map { it.title }

        assertEquals(listOf("A step"), titles)
    }

    @Test
    fun `resolves by id`() {
        val phase = addPhase(position = 0, title = "Phase")
        val step = addStep(phase, "Set up your laptop", position = 0)
        stubPath()

        assertEquals(step.id, reader.resolve(userId, step.id.toString())?.id)
    }

    @Test
    fun `resolves by exact title, trimmed and case-insensitive`() {
        val phase = addPhase(position = 0, title = "Phase")
        val step = addStep(phase, "Set up your laptop", position = 0)
        stubPath()

        assertEquals(step.id, reader.resolve(userId, "  SET UP your Laptop  ")?.id)
    }

    @Test
    fun `resolves by a title with collapsed whitespace`() {
        val phase = addPhase(position = 0, title = "Phase")
        val step = addStep(phase, "Set   up your laptop", position = 0)
        stubPath()

        assertEquals(step.id, reader.resolve(userId, "Set up your laptop")?.id)
    }

    @Test
    fun `resolves by a unique partial title`() {
        val phase = addPhase(position = 0, title = "Phase")
        val step = addStep(phase, "Set up your development laptop", position = 0)
        stubPath()

        assertEquals(step.id, reader.resolve(userId, "development laptop")?.id)
    }

    @Test
    fun `an ambiguous title resolves to nothing`() {
        val phase = addPhase(position = 0, title = "Phase")
        addStep(phase, "Meet your team lead", position = 0)
        addStep(phase, "Meet your team buddy", position = 1)
        stubPath()

        assertNull(reader.resolve(userId, "meet your team"))
    }

    @Test
    fun `an unmatched title resolves to nothing`() {
        val phase = addPhase(position = 0, title = "Phase")
        addStep(phase, "Set up your laptop", position = 0)
        stubPath()

        assertNull(reader.resolve(userId, "Learn the deploy pipeline"))
    }

    @Test
    fun `titlesFor names every visible step`() {
        val phase = addPhase(position = 0, title = "Phase")
        addStep(phase, "Set up your laptop", position = 0)
        addStep(phase, "Meet your team", position = 1)
        stubPath()

        assertEquals(listOf("Set up your laptop", "Meet your team"), reader.titlesFor(userId))
    }
}
