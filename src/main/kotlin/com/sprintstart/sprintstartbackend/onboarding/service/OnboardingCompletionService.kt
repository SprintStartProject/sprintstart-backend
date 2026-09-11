package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.mapper.isCompleteFor
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Promotes a user to "onboarded" once every phase of their path is complete.
 *
 * Completion is the same judgement the path mapper uses to unlock phases: all steps of
 * every phase finished or skipped, and every knowledge-check question answered correctly
 * at least once. It is re-evaluated after each step completion and each question attempt,
 * so onboarding can end on the last step just as well as on the last question.
 */
@Service
class OnboardingCompletionService(
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val questionAttemptRepository: QuestionAttemptRepository,
    private val userApi: UserApi,
) {
    /**
     * Marks the user onboarded when nothing is left to do, and reports whether that
     * happened in this call.
     *
     * @param userId Identifier of the user to evaluate.
     * @return true when the user counts as onboarded, false while anything is still open.
     */
    @Transactional
    fun completeIfFinished(userId: UUID): Boolean {
        val phases = onboardingPhaseRepository.findAllByPathUserId(userId)
        if (phases.isEmpty()) return false

        val passedQuestionIds = questionAttemptRepository.findPassedQuestionIdsByUserId(userId).toSet()
        if (!phases.all { it.isCompleteFor(passedQuestionIds) }) return false

        userApi.markOnboardingCompleted(userId)
        return true
    }
}
