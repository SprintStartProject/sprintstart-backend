package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class SkipTeamActionsTest {
    private val f = ContentFixture()
    private val skipService: OnboardingSkipService = mockk(relaxed = true)
    private val skipId = UUID.randomUUID()

    private fun pending(
        status: SkipStatus = SkipStatus.PENDING,
        stepStatus: StepStatus = StepStatus.WAITING,
        owner: UUID = f.memberId,
    ) {
        f.element(PathElementKind.SKIP, skipId, owner = owner, title = "Install", stepStatus = stepStatus)
        every { skipService.getSkipById(skipId) } returns
            GetOnboardingSkipResponse(skipId, UUID.randomUUID(), status, "I already know the toolchain")
    }

    @Nested
    inner class Accept {
        private val action = AcceptSkipAction(f.scope, skipService)

        @Test
        fun `shows the hire's reason and what accepting does to the step`() {
            pending()

            val draft = f.proposed(
                action.draft(
                    f.call("accept_skip", "skip_id" to skipId, "review_comment" to "Fine, go ahead"),
                    f.context,
                ),
            )

            assertThat(action.risk).isEqualTo(BuddyProposalRisk.STANDARD)
            assertThat(draft.preview).contains(
                "Accept Sam Rivera's request to skip the step “Install”",
                "“I already know the toolchain”",
                "“Fine, go ahead”",
                "marked skipped and counts as done",
                "finishes their onboarding",
            )
        }

        @Test
        fun `a comment is optional for an acceptance`() {
            pending()

            val draft = f.proposed(action.draft(f.call("accept_skip", "skip_id" to skipId), f.context))

            assertThat(draft.preview).contains("No comment goes with it")
        }

        @Test
        fun `a request that was already answered is refused, at proposal and at confirm`() {
            pending(status = SkipStatus.ACCEPTED)

            assertThat(f.refusal(action.draft(f.call("accept_skip", "skip_id" to skipId), f.context)))
                .contains("already been answered")
            assertThat(action.recheck(f.json("skip_id" to skipId), f.context)).contains("already been answered")
        }

        @Test
        fun `a request from somebody off the project is refused, and looks the same as one that is not there`() {
            pending(owner = f.outsiderId)
            val missing = UUID.randomUUID()
            f.gone(PathElementKind.SKIP, missing)

            val outsider = f.refusal(action.draft(f.call("accept_skip", "skip_id" to skipId), f.context))
            val absent = f.refusal(action.draft(f.call("accept_skip", "skip_id" to missing), f.context))

            assertThat(outsider).contains("not on the onboarding path of anybody on this project")
            assertThat(absent).isEqualTo(outsider)
        }

        @Test
        fun `a request deleted between the scope check and the read is refused, not thrown`() {
            f.element(PathElementKind.SKIP, skipId)
            every { skipService.getSkipById(skipId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertThat(action.recheck(f.json("skip_id" to skipId), f.context)).contains("is gone")
        }

        @Test
        fun `says so when the hire's path is shared with other projects`() {
            pending()
            f.alsoOn("Payments")

            val draft = f.proposed(action.draft(f.call("accept_skip", "skip_id" to skipId), f.context))

            assertThat(draft.preview).contains("also on Payments")
        }

        @Test
        fun `performing accepts with the stored comment`() =
            runTest {
                val request = slot<ReviewOnboardingSkipRequest>()
                every { skipService.acceptSkipById(skipId, capture(request)) } returns mockk(relaxed = true)

                action.perform(f.json("skip_id" to skipId, "review_comment" to "ok"), f.context)

                assertThat(request.captured.reviewComment).isEqualTo("ok")
            }
    }

    @Nested
    inner class Deny {
        private val action = DenySkipAction(f.scope, skipService)

        @Test
        fun `a denial without a comment is refused, because the hire is shown it`() {
            pending()

            val reason = f.refusal(action.draft(f.call("deny_skip", "skip_id" to skipId), f.context))

            assertThat(reason).contains("needs a comment")
        }

        @Test
        fun `a blank comment is no comment`() {
            pending()

            val reason = f.refusal(
                action.draft(f.call("deny_skip", "skip_id" to skipId, "review_comment" to "   "), f.context),
            )

            assertThat(reason).contains("needs a comment")
        }

        @Test
        fun `shows the comment in full`() {
            pending()
            val comment = "Please do this one, it is where the team's conventions are explained. " +
                "Ask on the channel if you get stuck."

            val draft = f.proposed(
                action.draft(f.call("deny_skip", "skip_id" to skipId, "review_comment" to comment), f.context),
            )

            assertThat(draft.preview).contains("They are told: “$comment”", "stays for them to do")
            assertThat(draft.params.text("review_comment")).isEqualTo(comment)
        }

        @Test
        fun `says when a step they had started goes back to waiting`() {
            pending(stepStatus = StepStatus.IN_PROGRESS)

            val draft = f.proposed(
                action.draft(f.call("deny_skip", "skip_id" to skipId, "review_comment" to "no"), f.context),
            )

            assertThat(draft.preview).contains("Sam Rivera had started it", "back to waiting")
        }

        @Test
        fun `a step that was only waiting makes no such claim`() {
            pending(stepStatus = StepStatus.WAITING)

            val draft = f.proposed(
                action.draft(f.call("deny_skip", "skip_id" to skipId, "review_comment" to "no"), f.context),
            )

            assertThat(draft.preview).doesNotContain("had started")
        }

        @Test
        fun `performing denies with the stored comment`() =
            runTest {
                val request = slot<ReviewOnboardingSkipRequest>()
                every { skipService.denySkipById(skipId, capture(request)) } returns mockk(relaxed = true)

                action.perform(f.json("skip_id" to skipId, "review_comment" to "not this one"), f.context)

                assertThat(request.captured.reviewComment).isEqualTo("not this one")
            }
    }

    @Nested
    inner class Delete {
        private val action = DeleteSkipAction(f.scope, skipService)

        @Test
        fun `is destructive and says the hire is not told`() {
            pending()

            val draft = f.proposed(action.draft(f.call("delete_skip", "skip_id" to skipId), f.context))

            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
            assertThat(draft.preview).contains("without answering it", "not told anything", "cannot be undone")
        }

        @Test
        fun `only a pending request can be deleted`() {
            pending(status = SkipStatus.DENIED)

            assertThat(f.refusal(action.draft(f.call("delete_skip", "skip_id" to skipId), f.context)))
                .contains("already been answered")
        }

        @Test
        fun `drafting deletes nothing, performing deletes the request named`() =
            runTest {
                pending()

                action.draft(f.call("delete_skip", "skip_id" to skipId), f.context)
                verify(exactly = 0) { skipService.deleteSkipById(any()) }

                action.perform(f.json("skip_id" to skipId), f.context)
                verify { skipService.deleteSkipById(skipId) }
            }
    }
}
