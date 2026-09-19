package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingCompletionServiceTest {
    private val onboardingPhaseRepository: OnboardingPhaseRepository = mockk()
    private val questionAttemptRepository: QuestionAttemptRepository = mockk()
    private val userApi: UserApi = mockk(relaxed = true)
    private val service = OnboardingCompletionService(
        onboardingPhaseRepository,
        questionAttemptRepository,
        userApi,
    )

    private val userId = UUID.randomUUID()

    private fun makePhase(stepStatuses: List<StepStatus>): OnboardingPhase {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Phase", description = "Desc")
        path.phases += phase
        stepStatuses.forEachIndexed { index, status ->
            phase.steps += OnboardingStep(
                phase = phase,
                position = index,
                title = "Step $index",
                description = "Desc",
                type = StepType.DOCUMENT,
                estimatedMinutes = 10,
                expectedOutcome = "Outcome",
                status = status,
            )
        }
        return phase
    }

    @Test
    fun `marks the user onboarded when every phase is complete`() {
        val phase = makePhase(listOf(StepStatus.FINISHED, StepStatus.SKIPPED))
        val question = PhaseCheckQuestion(
            phase = phase,
            position = 0,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "q",
        )
        phase.checkQuestions += question
        every { onboardingPhaseRepository.findAllByPathUserId(userId) } returns mutableListOf(phase)
        every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns listOf(question.id)

        val completed = service.completeIfFinished(userId)

        assertTrue(completed)
        verify { userApi.markOnboardingCompleted(userId) }
    }

    @Test
    fun `returns false when the user has no phases`() {
        every { onboardingPhaseRepository.findAllByPathUserId(userId) } returns mutableListOf()

        val completed = service.completeIfFinished(userId)

        assertFalse(completed)
        verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
    }

    @Test
    fun `returns false while any step is unfinished`() {
        val phase = makePhase(listOf(StepStatus.FINISHED, StepStatus.IN_PROGRESS))
        every { onboardingPhaseRepository.findAllByPathUserId(userId) } returns mutableListOf(phase)
        every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns emptyList()

        val completed = service.completeIfFinished(userId)

        assertFalse(completed)
        verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
    }

    @Test
    fun `returns false while any question is unpassed`() {
        val phase = makePhase(listOf(StepStatus.FINISHED))
        val question = PhaseCheckQuestion(
            phase = phase,
            position = 0,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "q",
        )
        phase.checkQuestions += question
        every { onboardingPhaseRepository.findAllByPathUserId(userId) } returns mutableListOf(phase)
        // The question was attempted but never answered correctly.
        every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns emptyList()

        val completed = service.completeIfFinished(userId)

        assertFalse(completed)
        verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
    }

    @Test
    fun `returns false while any later phase is still open`() {
        val done = makePhase(listOf(StepStatus.FINISHED))
        val open = makePhase(listOf(StepStatus.WAITING))
        every { onboardingPhaseRepository.findAllByPathUserId(userId) } returns mutableListOf(done, open)
        every { questionAttemptRepository.findPassedQuestionIdsByUserId(userId) } returns emptyList()

        val completed = service.completeIfFinished(userId)

        assertFalse(completed)
        verify(exactly = 0) { userApi.markOnboardingCompleted(any()) }
    }
}
