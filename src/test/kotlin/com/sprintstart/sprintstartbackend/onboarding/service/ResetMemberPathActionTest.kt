package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResetMemberPathActionTest {
    private val f = ContentFixture()
    private val pathService: OnboardingPathService = mockk(relaxed = true)
    private val action = ResetMemberPathAction(f.scope, f.pathElements, pathService)

    private fun call(id: Any) = f.call("reset_member_path", "member_id" to id)

    @Test
    fun `is destructive`() {
        assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
    }

    @Test
    fun `puts numbers on how much is thrown away, including progress`() {
        every { f.pathElements.pathOf(f.memberId) } returns
            PathSummary(phases = 4, steps = 17, finishedSteps = 9, checks = 6)

        val draft = f.proposed(action.draft(call(f.memberId), f.context))

        assertThat(draft.preview).contains(
            "Sam Rivera's whole onboarding path",
            "4 phases and 17 steps",
            "6 knowledge-check questions",
            "got through 9 of those steps",
            "cannot be undone",
        )
        assertThat(draft.preview).contains("Nothing here makes a new path")
    }

    @Test
    fun `a path nobody has started makes no claim about progress`() {
        every { f.pathElements.pathOf(f.memberId) } returns PathSummary(1, 1, 0, 0)

        val draft = f.proposed(action.draft(call(f.memberId), f.context))

        assertThat(draft.preview).contains("1 phase and 1 step").doesNotContain("got through")
    }

    @Test
    fun `says so when the path is shared with other projects`() {
        every { f.pathElements.pathOf(f.memberId) } returns PathSummary(1, 1, 0, 0)
        f.alsoOn("Payments")

        val draft = f.proposed(action.draft(call(f.memberId), f.context))

        assertThat(draft.preview).contains("also on Payments", "changes it there too")
    }

    @Test
    fun `refuses somebody not on the project, and somebody with no path`() {
        assertThat(f.refusal(action.draft(call(f.outsiderId), f.context))).contains("not on this project")

        every { f.pathElements.pathOf(f.memberId) } returns null
        assertThat(f.refusal(action.draft(call(f.memberId), f.context))).contains("no onboarding path")
    }

    @Test
    fun `a confirm for somebody who left, or whose path is already gone, is turned down`() {
        assertThat(action.recheck(f.json("member_id" to f.outsiderId), f.context)).contains("no longer on this project")

        every { f.pathElements.pathOf(f.memberId) } returns null
        assertThat(action.recheck(f.json("member_id" to f.memberId), f.context)).contains("already gone")
    }

    @Test
    fun `a live path passes the recheck`() {
        every { f.pathElements.pathOf(f.memberId) } returns PathSummary(1, 1, 0, 0)

        assertThat(action.recheck(f.json("member_id" to f.memberId), f.context)).isNull()
    }

    @Test
    fun `drafting deletes nothing, performing deletes the path of the member named`() =
        runTest {
            every { f.pathElements.pathOf(f.memberId) } returns PathSummary(1, 1, 0, 0)

            action.draft(call(f.memberId), f.context)
            verify(exactly = 0) { pathService.deleteOnboardingPathByUserId(any()) }

            action.perform(f.json("member_id" to f.memberId), f.context)
            verify { pathService.deleteOnboardingPathByUserId(f.memberId) }
        }
}
