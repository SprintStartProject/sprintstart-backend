package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.util.UUID

class OnboardingPositionReaderTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val reader = OnboardingPositionReader(onboardingPathRepository)

    private val userId = UUID.randomUUID()

    private fun phase(path: OnboardingPath, position: Int, title: String): OnboardingPhase {
        val phase = OnboardingPhase(
            path = path,
            position = position,
            title = title,
            description = "Description of $title",
        )
        path.phases += phase
        return phase
    }

    private fun step(phase: OnboardingPhase, position: Int, title: String, status: StepStatus): OnboardingStep {
        val step = OnboardingStep(
            phase = phase,
            position = position,
            title = title,
            description = "Description of $title",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Outcome",
            status = status,
        )
        phase.steps += step
        return step
    }

    @Test
    fun `the active step is the first unfinished one, in phase and step order`() {
        val path = OnboardingPath(userId = userId)
        // Deliberately added out of order: the rule sorts by position, and a caller that loaded the
        // path without the entity's @OrderBy must still get the same answer.
        val second = phase(path, position = 1, title = "Second phase")
        val first = phase(path, position = 0, title = "First phase")

        step(first, position = 1, title = "Later in the first phase", status = StepStatus.WAITING)
        step(first, position = 0, title = "Read the handbook", status = StepStatus.FINISHED)
        step(second, position = 0, title = "Ship something", status = StepStatus.WAITING)

        val active = reader.activeStepIn(path)

        assertThat(active?.phase?.title).isEqualTo("First phase")
        assertThat(active?.step?.title).isEqualTo("Later in the first phase")
    }

    @Test
    fun `an in-progress step counts as the active one`() {
        val path = OnboardingPath(userId = userId)
        val only = phase(path, position = 0, title = "Getting started")
        step(only, position = 0, title = "Set up your machine", status = StepStatus.IN_PROGRESS)

        assertThat(reader.activeStepIn(path)?.step?.title).isEqualTo("Set up your machine")
    }

    @Test
    fun `a path with nothing left has no active step`() {
        val path = OnboardingPath(userId = userId)
        val only = phase(path, position = 0, title = "Getting started")
        step(only, position = 0, title = "Read the handbook", status = StepStatus.FINISHED)
        step(only, position = 1, title = "Optional tour", status = StepStatus.SKIPPED)

        assertThat(reader.activeStepIn(path)).isNull()
    }

    @Test
    fun `an empty path has no active step and no progress`() {
        val path = OnboardingPath(userId = userId)

        assertThat(reader.activeStepIn(path)).isNull()
        assertThat(reader.progressOf(path)).isEqualTo(0.0)
    }

    @Test
    fun `skipped steps count as behind the hire, not as outstanding work`() {
        val path = OnboardingPath(userId = userId)
        val first = phase(path, position = 0, title = "Getting started")
        val second = phase(path, position = 1, title = "First contribution")
        step(first, position = 0, title = "Read the handbook", status = StepStatus.FINISHED)
        step(first, position = 1, title = "Optional tour", status = StepStatus.SKIPPED)
        step(second, position = 0, title = "Ship something", status = StepStatus.WAITING)
        step(second, position = 1, title = "Review somebody", status = StepStatus.WAITING)

        assertThat(reader.progressOf(path)).isCloseTo(0.5, within(1e-9))
    }

    @Test
    fun `positions are read once for the whole set and keyed by user`() {
        val other = UUID.randomUUID()

        val mine = OnboardingPath(userId = userId)
        val minePhase = phase(mine, position = 0, title = "Getting started")
        step(minePhase, position = 0, title = "Set up your machine", status = StepStatus.IN_PROGRESS)
        step(minePhase, position = 1, title = "Meet the team", status = StepStatus.WAITING)

        val theirs = OnboardingPath(userId = other)
        val theirPhase = phase(theirs, position = 0, title = "First contribution")
        step(theirPhase, position = 0, title = "Ship something", status = StepStatus.FINISHED)

        every { onboardingPathRepository.findByUserIdIn(listOf(userId, other)) } returns listOf(mine, theirs)

        val positions = reader.positionsFor(listOf(userId, other, userId))

        assertThat(positions[userId]?.currentPhase).isEqualTo("Getting started")
        assertThat(positions[userId]?.currentStep).isEqualTo("Set up your machine")
        assertThat(positions[userId]?.progressPercentage).isEqualTo(0.0)
        assertThat(positions[other]?.currentStep).isNull()
        assertThat(positions[other]?.progressPercentage).isEqualTo(1.0)
    }

    @Test
    fun `somebody without a path is absent rather than reported as at the beginning`() {
        every { onboardingPathRepository.findByUserIdIn(listOf(userId)) } returns emptyList()

        assertThat(reader.positionsFor(listOf(userId))).isEmpty()
    }

    @Test
    fun `an empty request set reads nothing at all`() {
        assertThat(reader.positionsFor(emptyList())).isEmpty()
        // No repository call to verify against — the mock has no stub, so any call would fail here.
    }
}
