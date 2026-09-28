package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

/**
 * A step added to a member's path goes *into* the phase's dependency graph.
 *
 * Testing found a refresher the hire asked to do next sitting unconnected in the graph view -- open
 * from the start, and never what came next. These cases are about the edges, on real entities,
 * because the edges are what the lock computation reads.
 */
class OnboardingStepPlacementServiceTest {
    private val onboardingStepService: OnboardingStepService = mockk()
    private val onboardingStepRepository: OnboardingStepRepository = mockk()
    private val service = OnboardingStepPlacementService(onboardingStepService, onboardingStepRepository)

    private val path = OnboardingPath(userId = UUID.randomUUID())
    private val phase = OnboardingPhase(path = path, position = 0, title = "Setup", description = "d")

    @Test
    fun `a step put between two items replaces the edge that ran past it`() {
        val current = step(0, graphX = 0.0, graphY = 0.0)
        val later = step(1, graphX = 0.0, graphY = 400.0).also { it.blockedBy += current }
        val added = arrange(position = 1)

        service.createConnectedStepForPhase(
            phase.id,
            request(1),
            waitsOn = setOf(current.id),
            unlocks = setOf(later.id),
            graphX = null,
            graphY = null,
        )

        assertThat(added.blockedBy.map { it.id }).containsExactly(current.id)
        // current → added → later, not current → later as well: the shortcut would read as "later
        // does not really need the step that was just put in its way".
        assertThat(later.blockedBy.map { it.id }).containsExactly(added.id)
        // Drawn between them, in a graph that flows top to bottom.
        assertThat(added.graphX).isEqualTo(0.0)
        assertThat(added.graphY).isEqualTo(200.0)
    }

    @Test
    fun `a refresher can be put in front of a question`() {
        val question = PhaseCheckQuestion(
            phase = phase,
            position = 0,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "Who runs the retro?",
        ).also { phase.checkQuestions += it }
        val added = arrange(position = 0)

        service.createConnectedStepForPhase(
            phase.id,
            request(0),
            waitsOn = emptySet(),
            unlocks = setOf(question.id),
            graphX = null,
            graphY = null,
        )

        assertThat(question.blockedBy.map { it.id }).containsExactly(added.id)
    }

    @Test
    fun `a placement that would make a loop is refused`() {
        val first = step(0)
        val second = step(1).also { it.blockedBy += first }
        arrange(position = 2)

        assertThatThrownBy {
            service.createConnectedStepForPhase(
                phase.id,
                request(2),
                waitsOn = setOf(second.id),
                unlocks = setOf(first.id),
                graphX = null,
                graphY = null,
            )
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `an item from another phase is refused`() {
        arrange(position = 0)

        assertThatThrownBy {
            service.createConnectedStepForPhase(
                phase.id,
                request(0),
                waitsOn = setOf(UUID.randomUUID()),
                unlocks = emptySet(),
                graphX = null,
                graphY = null,
            )
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a step lands where it was dropped when the PM dropped it somewhere`() {
        val current = step(0, graphX = 0.0, graphY = 0.0)
        val later = step(1, graphX = 0.0, graphY = 400.0).also { it.blockedBy += current }
        val added = step(1, title = "Refresher")
        every { onboardingStepService.createOnboardingStepForPhaseId(phase.id, any()) } returns
            CreateOnboardingStepResponse(
                id = added.id,
                phaseId = phase.id,
                position = 1,
                title = added.title,
                description = "",
                type = StepType.TASK,
                estimatedMinutes = 15,
                isAiAssisted = false,
                expectedOutcome = "",
                status = StepStatus.WAITING,
            )
        every { onboardingStepRepository.findById(added.id) } returns Optional.of(added)

        service.createConnectedStepForPhase(
            phase.id,
            request(1),
            waitsOn = setOf(current.id),
            unlocks = setOf(later.id),
            graphX = 320.0,
            graphY = 180.0,
        )

        assertThat(later.blockedBy.map { it.id }).containsExactly(added.id)
        assertThat(added.graphX).isEqualTo(320.0)
        assertThat(added.graphY).isEqualTo(180.0)
    }

    /** Stubs the plain creation to hand back a real entity in [phase], the way the repository would. */
    private fun arrange(position: Int): OnboardingStep {
        val added = step(position, title = "Refresher")
        every { onboardingStepService.createOnboardingStepForPhaseId(phase.id, any()) } returns
            CreateOnboardingStepResponse(
                id = added.id,
                phaseId = phase.id,
                position = position,
                title = added.title,
                description = "",
                type = StepType.TASK,
                estimatedMinutes = 15,
                isAiAssisted = false,
                expectedOutcome = "",
                status = StepStatus.WAITING,
            )
        every { onboardingStepRepository.findById(added.id) } returns Optional.of(added)
        return added
    }

    private fun step(
        position: Int,
        title: String = "Step $position",
        graphX: Double? = null,
        graphY: Double? = null,
    ) = OnboardingStep(
        phase = phase,
        position = position,
        title = title,
        description = "d",
        type = StepType.TASK,
        estimatedMinutes = 10,
        expectedOutcome = "",
        status = StepStatus.WAITING,
        graphX = graphX,
        graphY = graphY,
    ).also { phase.steps += it }

    private fun request(position: Int) = CreateOnboardingStepRequest(
        position = position,
        title = "Refresher",
        description = "What to revisit.",
        type = StepType.TASK,
        estimatedMinutes = 15,
        expectedOutcome = "",
    )
}
