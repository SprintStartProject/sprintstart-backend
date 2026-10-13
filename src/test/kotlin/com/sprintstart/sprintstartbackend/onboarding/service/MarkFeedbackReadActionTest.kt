package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MarkFeedbackReadActionTest {
    private val f = ContentFixture()
    private val feedbackService: OnboardingFeedbackService = mockk(relaxed = true)
    private val action = MarkFeedbackReadAction(f.scope, feedbackService)
    private val feedbackId = UUID.randomUUID()

    private fun feedback(read: Boolean = false, stepTitle: String? = "Install") {
        f.element(PathElementKind.FEEDBACK, feedbackId, owner = f.memberId)
        every { feedbackService.getAllFeedbackByUserId(f.memberId) } returns
            listOf(
                GetAdminOnboardingFeedbackResponse(
                    id = feedbackId,
                    userId = f.memberId,
                    stepId = UUID.randomUUID(),
                    stepTitle = stepTitle,
                    message = "This step assumed I had Docker",
                    read = read,
                    createdAt = Instant.now(),
                ),
            )
    }

    private fun call(id: Any = feedbackId) = f.call("mark_feedback_read", "feedback_id" to id)

    @Test
    fun `shows the message being marked and says nobody is told`() {
        feedback()

        val draft = f.proposed(action.draft(call(), f.context))

        assertThat(draft.preview).contains(
            "feedback from Sam Rivera",
            "“Install”",
            "“This step assumed I had Docker”",
            "They are not told",
        )
    }

    @Test
    fun `feedback on no step is described as about the path as a whole`() {
        feedback(stepTitle = null)

        assertThat(f.proposed(action.draft(call(), f.context)).preview).contains("their path as a whole")
    }

    @Test
    fun `feedback already read is refused`() {
        feedback(read = true)

        assertThat(f.refusal(action.draft(call(), f.context))).contains("already marked as read")
    }

    @Test
    fun `feedback from somebody off the project is refused, and looks the same as feedback that is not there`() {
        f.element(PathElementKind.FEEDBACK, feedbackId, owner = f.outsiderId)
        val missing = UUID.randomUUID()
        f.gone(PathElementKind.FEEDBACK, missing)

        val outsider = f.refusal(action.draft(call(), f.context))
        val absent = f.refusal(action.draft(call(missing), f.context))

        assertThat(outsider).contains("list_feedback")
        assertThat(absent).isEqualTo(outsider)
    }

    @Test
    fun `a confirm for feedback somebody else already read is turned down`() {
        feedback(read = true)

        assertThat(action.recheck(f.json("feedback_id" to feedbackId), f.context))
            .contains("already marked that feedback as read")
    }

    @Test
    fun `a confirm for unread feedback still on a member's path passes`() {
        feedback()

        assertThat(action.recheck(f.json("feedback_id" to feedbackId), f.context)).isNull()
    }

    @Test
    fun `drafting marks nothing, performing marks the feedback named`() =
        runTest {
            feedback()

            action.draft(call(), f.context)
            verify(exactly = 0) { feedbackService.markFeedbackAsRead(any()) }

            action.perform(f.json("feedback_id" to feedbackId), f.context)
            verify { feedbackService.markFeedbackAsRead(feedbackId) }
        }
}
