package com.sprintstart.sprintstartbackend.path.listener

import com.sprintstart.sprintstartbackend.path.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.path.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID

class PathUserDeletedListenerTest {
    private val pathRepository: OnboardingPathRepository = mockk(relaxed = true)
    private val attemptRepository: QuestionAttemptRepository = mockk(relaxed = true)
    private val feedbackRepository: OnboardingFeedbackRepository = mockk(relaxed = true)
    private val listener = PathUserDeletedListener(pathRepository, attemptRepository, feedbackRepository)
    private val userId = UUID.randomUUID()

    /** The path describes the person it was built for and is reachable by nobody once they are gone. */
    @Test
    fun `deleting a user erases their path, their check answers and their feedback`() {
        listener.onUserDeleted(UserDeletedEvent(userId))

        verify { pathRepository.deleteByUserId(userId) }
        verify { attemptRepository.deleteAllByUserId(userId) }
        verify { feedbackRepository.deleteAllByUserId(userId) }
    }

    /** Feedback on a step points at that step, so it goes before the path cascades the step away. */
    @Test
    fun `feedback goes before the path whose steps it points at`() {
        listener.onUserDeleted(UserDeletedEvent(userId))

        verifyOrder {
            feedbackRepository.deleteAllByUserId(userId)
            pathRepository.deleteByUserId(userId)
        }
    }
}
