package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ArrangeOnboardingGraphRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.OnboardingGraphNodePosition
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ReplaceOnboardingBlockersRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingSubGraphNodeRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

/**
 * Layout and edges of an onboarding path's graphs, on real entities: the edges are what the lock
 * computation reads, so a loop let through here is a phase nobody can finish.
 */
class OnboardingGraphServiceTest {
    private val userApi: UserApi = mockk()
    private val pathRepository: OnboardingPathRepository = mockk()
    private val phaseRepository: OnboardingPhaseRepository = mockk()
    private val nodeRepository: OnboardingSubGraphNodeRepository = mockk()
    private val service = OnboardingGraphService(userApi, pathRepository, phaseRepository, nodeRepository)

    private val authId = "auth|hire"
    private val userId = UUID.randomUUID()
    private val path = OnboardingPath(userId = userId)
    private val phase = OnboardingPhase(path = path, position = 0, title = "Setup", description = "d")

    @Test
    fun `a hire can arrange the steps and questions of their own phase`() {
        val first = step(0)
        val question = question(0)
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { phaseRepository.findByIdAndPathUserId(phase.id, userId) } returns Optional.of(phase)

        service.arrangePhaseForMe(
            authId,
            phase.id,
            ArrangeOnboardingGraphRequest(
                listOf(
                    OnboardingGraphNodePosition(first.id, 10.0, 20.0),
                    OnboardingGraphNodePosition(question.id, 30.0, 220.0),
                ),
            ),
        )

        assertThat(first.graphX to first.graphY).isEqualTo(10.0 to 20.0)
        assertThat(question.graphX to question.graphY).isEqualTo(30.0 to 220.0)
    }

    @Test
    fun `arranging refuses a node from another phase`() {
        every { phaseRepository.findById(phase.id) } returns Optional.of(phase)

        assertThatThrownBy {
            service.arrangePhaseById(
                phase.id,
                ArrangeOnboardingGraphRequest(listOf(OnboardingGraphNodePosition(UUID.randomUUID(), 0.0, 0.0))),
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `arranging refuses positions that are not finite`() {
        val first = step(0)
        every { phaseRepository.findById(phase.id) } returns Optional.of(phase)

        assertThatThrownBy {
            service.arrangePhaseById(
                phase.id,
                ArrangeOnboardingGraphRequest(listOf(OnboardingGraphNodePosition(first.id, Double.NaN, 0.0))),
            )
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `replacing blockers swaps the whole set`() {
        val first = step(0)
        val second = step(1)
        val third = step(2).also { it.blockedBy += first }
        every { nodeRepository.findById(third.id) } returns Optional.of(third)

        val response = service.replaceNodeBlockers(third.id, ReplaceOnboardingBlockersRequest(setOf(second.id)))

        assertThat(third.blockedBy.map { it.id }).containsExactly(second.id)
        assertThat(response.blockerIds).containsExactly(second.id)
    }

    @Test
    fun `a blocker that already waits on the node is refused as a loop`() {
        val first = step(0)
        val second = step(1).also { it.blockedBy += first }
        val third = step(2).also { it.blockedBy += second }
        every { nodeRepository.findById(first.id) } returns Optional.of(first)

        assertThatThrownBy {
            service.replaceNodeBlockers(first.id, ReplaceOnboardingBlockersRequest(setOf(third.id)))
        }.isInstanceOf(ResponseStatusException::class.java)
        assertThat(first.blockedBy).isEmpty()
    }

    @Test
    fun `a node cannot wait on itself`() {
        val first = step(0)
        every { nodeRepository.findById(first.id) } returns Optional.of(first)

        assertThatThrownBy {
            service.replaceNodeBlockers(first.id, ReplaceOnboardingBlockersRequest(setOf(first.id)))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `phase blockers refuse loops across the path`() {
        val intro = phase
        val setup = OnboardingPhase(path = path, position = 1, title = "Env", description = "d")
            .also { it.blockedBy += intro }
        every { phaseRepository.findById(intro.id) } returns Optional.of(intro)
        every { phaseRepository.findAllByPathId(path.id) } returns mutableListOf(intro, setup)

        assertThatThrownBy {
            service.replacePhaseBlockers(intro.id, ReplaceOnboardingBlockersRequest(setOf(setup.id)))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `phase blockers can be replaced within the path`() {
        val intro = phase
        val meetings = OnboardingPhase(path = path, position = 1, title = "Meetings", description = "d")
        val setup = OnboardingPhase(path = path, position = 2, title = "Env", description = "d")
            .also { it.blockedBy += intro }
        every { phaseRepository.findById(setup.id) } returns Optional.of(setup)
        every { phaseRepository.findAllByPathId(path.id) } returns mutableListOf(intro, meetings, setup)

        service.replacePhaseBlockers(setup.id, ReplaceOnboardingBlockersRequest(setOf(intro.id, meetings.id)))

        assertThat(setup.blockedBy.map { it.id }).containsExactlyInAnyOrder(intro.id, meetings.id)
    }

    private fun step(position: Int) = OnboardingStep(
        phase = phase,
        position = position,
        title = "Step $position",
        description = "d",
        type = StepType.TASK,
        estimatedMinutes = 10,
        expectedOutcome = "",
        status = StepStatus.WAITING,
    ).also { phase.steps += it }

    private fun question(position: Int) = PhaseCheckQuestion(
        phase = phase,
        position = position,
        type = CheckQuestionType.SHORT_TEXT,
        question = "Question $position",
    ).also { phase.checkQuestions += it }
}
