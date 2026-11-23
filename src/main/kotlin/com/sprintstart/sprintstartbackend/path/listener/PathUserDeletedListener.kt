package com.sprintstart.sprintstartbackend.path.listener

import com.sprintstart.sprintstartbackend.path.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.path.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/**
 * Erases a deleted user's onboarding path, their answers to its checks, and the feedback they gave.
 *
 * Deleting the path cascades to its phases and steps, and with the steps to their tasks, resources,
 * skip requests and the feedback given on them. Feedback not tied to a step and the check answers hang
 * on the user alone, so they are deleted by user.
 */
@Component
class PathUserDeletedListener(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val questionAttemptRepository: QuestionAttemptRepository,
    private val onboardingFeedbackRepository: OnboardingFeedbackRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        onboardingFeedbackRepository.deleteAllByUserId(event.userId)
        questionAttemptRepository.deleteAllByUserId(event.userId)
        onboardingPathRepository.deleteByUserId(event.userId)
    }
}
