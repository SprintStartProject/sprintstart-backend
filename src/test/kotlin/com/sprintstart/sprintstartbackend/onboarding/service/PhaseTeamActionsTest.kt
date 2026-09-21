package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class PhaseTeamActionsTest {
    private val f = ContentFixture()
    private val phaseService: OnboardingPhaseService = mockk(relaxed = true)

    private fun summary(id: UUID, position: Int) =
        GetOnboardingPhasesResponse(
            id = id,
            pathId = UUID.randomUUID(),
            position = position,
            title = "t$position",
            description = "",
        )

    private fun current(id: UUID, position: Int = 1, title: String = "Setup", description: String = "Get going") {
        every { phaseService.getOnboardingPhaseById(id) } returns
            GetOnboardingPhaseResponse(
                id = id,
                pathId = UUID.randomUUID(),
                position = position,
                title = title,
                description = description,
                steps = emptyList(),
            )
    }

    @Nested
    inner class Add {
        private val action = AddPhaseAction(f.scope, f.pathElements, phaseService)

        private fun pathOf(phases: Int) {
            every { f.pathElements.pathOf(f.memberId) } returns PathSummary(phases, 0, 0, 0)
        }

        @Test
        fun `is a standard change in the content area`() {
            assertThat(action.area).isEqualTo(TeamArea.CONTENT)
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.STANDARD)
        }

        @Test
        fun `appends by default and says the phase starts empty`() {
            pathOf(3)

            val draft = f.proposed(
                action.draft(
                    f.call("add_phase", "member_id" to f.memberId, "title" to "Review", "description" to "How"),
                    f.context,
                ),
            )

            assertThat(draft.params.text("position")).isEqualTo("3")
            assertThat(draft.preview).contains("Sam Rivera", "“Review”", "place 4 of 4", "no steps")
            assertThat(draft.preview).doesNotContain("move down")
        }

        @Test
        fun `inserting before others says they move down`() {
            pathOf(3)

            val draft = f.proposed(
                action.draft(
                    f.call("add_phase", "member_id" to f.memberId, "title" to "First", "place" to 1),
                    f.context,
                ),
            )

            assertThat(draft.params.text("position")).isEqualTo("0")
            assertThat(draft.preview).contains("move down one place")
        }

        @Test
        fun `a place outside the path is refused`() {
            pathOf(3)

            val reason = f.refusal(
                action.draft(f.call("add_phase", "member_id" to f.memberId, "title" to "x", "place" to 9), f.context),
            )

            assertThat(reason).contains("from 1 to 4")
        }

        @Test
        fun `somebody not on the project is refused`() {
            val reason = f.refusal(
                action.draft(f.call("add_phase", "member_id" to f.outsiderId, "title" to "x"), f.context),
            )

            assertThat(reason).contains("not on this project")
        }

        @Test
        fun `a member with no path is refused rather than given one`() {
            every { f.pathElements.pathOf(f.memberId) } returns null

            val reason = f.refusal(
                action.draft(f.call("add_phase", "member_id" to f.memberId, "title" to "x"), f.context),
            )

            assertThat(reason).contains("no onboarding path")
        }

        @Test
        fun `a blank title is refused`() {
            pathOf(1)

            val reason = f.refusal(
                action.draft(f.call("add_phase", "member_id" to f.memberId, "title" to "  "), f.context),
            )

            assertThat(reason).contains("needs a title")
        }

        @Test
        fun `a member on other projects has it said in the preview`() {
            pathOf(1)
            f.alsoOn("Payments", "Search")

            val draft = f.proposed(
                action.draft(f.call("add_phase", "member_id" to f.memberId, "title" to "x"), f.context),
            )

            assertThat(
                draft.preview,
            ).contains("also on Payments, Search", "one onboarding path", "changes it there too")
        }

        @Test
        fun `nothing is written while drafting`() {
            pathOf(1)

            action.draft(f.call("add_phase", "member_id" to f.memberId, "title" to "x"), f.context)

            verify(exactly = 0) { phaseService.createOnboardingPhaseForUserId(any(), any()) }
        }

        @Test
        fun `a confirm for somebody who left is turned down`() {
            val params = f.json("member_id" to f.outsiderId, "title" to "x", "position" to 0)

            assertThat(action.recheck(params, f.context)).contains("no longer on this project")
        }

        @Test
        fun `a confirm for a place that has gone is turned down`() {
            pathOf(1)
            val params = f.json("member_id" to f.memberId, "title" to "x", "position" to 5)

            assertThat(action.recheck(params, f.context)).contains("fewer phases")
        }

        @Test
        fun `performing creates the phase with what was stored`() = runTest {
            val request = slot<CreateOnboardingPhaseRequest>()
            every { phaseService.createOnboardingPhaseForUserId(f.memberId, capture(request)) } returns
                mockk(relaxed = true)

            action.perform(
                f.json(
                    "member_id" to f.memberId,
                    "title" to "Review",
                    "description" to "How",
                    "position" to 2,
                ),
                f.context,
            )

            assertThat(request.captured).isEqualTo(CreateOnboardingPhaseRequest(2, "Review", "How"))
        }
    }

    @Nested
    inner class Update {
        private val action = UpdatePhaseAction(f.scope, phaseService)
        private val phaseId = UUID.randomUUID()

        private fun onMembersPath(siblings: Int = 3) {
            f.element(PathElementKind.PHASE, phaseId, title = "Setup", position = 1)
            current(phaseId)
            every { phaseService.getOnboardingPhasesForUser(f.memberId) } returns
                (0 until siblings).map { summary(UUID.randomUUID(), it) }
        }

        @Test
        fun `stores only what changes, and previews before and after`() {
            onMembersPath()

            val draft = f.proposed(
                action.draft(
                    f.call("update_phase", "phase_id" to phaseId, "title" to "Onboarding setup", "place" to 3),
                    f.context,
                ),
            )

            assertThat(draft.params.text("title")).isEqualTo("Onboarding setup")
            assertThat(draft.params.text("position")).isEqualTo("2")
            assertThat(draft.params.containsKey("description")).isFalse()
            assertThat(
                draft.preview,
            ).contains("“Setup” becomes “Onboarding setup”", "place 2 of 3 becomes place 3 of 3")
        }

        @Test
        fun `repeating what is already there is refused as no change`() {
            onMembersPath()

            val reason = f.refusal(
                action.draft(
                    f.call("update_phase", "phase_id" to phaseId, "title" to "Setup", "place" to 2),
                    f.context,
                ),
            )

            assertThat(reason).contains("Nothing would change")
        }

        @Test
        fun `an empty description is a request to clear it, and is kept`() {
            onMembersPath()

            val draft = f.proposed(
                action.draft(f.call("update_phase", "phase_id" to phaseId, "description" to ""), f.context),
            )

            assertThat(draft.params.containsKey("description")).isTrue()
            assertThat(draft.preview).contains("(empty)")
        }

        @Test
        fun `a phase on somebody else's path is refused, as is one that does not exist`() {
            f.element(PathElementKind.PHASE, phaseId, owner = f.outsiderId)
            val missing = UUID.randomUUID()
            f.gone(PathElementKind.PHASE, missing)

            val outsider = f.refusal(
                action.draft(f.call("update_phase", "phase_id" to phaseId, "title" to "x"), f.context),
            )
            val absent = f.refusal(
                action.draft(f.call("update_phase", "phase_id" to missing, "title" to "x"), f.context),
            )

            assertThat(outsider).contains("not on the onboarding path of anybody on this project")
            assertThat(absent).isEqualTo(outsider)
        }

        @Test
        fun `a place outside the path is refused`() {
            onMembersPath(siblings = 2)

            val reason = f.refusal(action.draft(f.call("update_phase", "phase_id" to phaseId, "place" to 7), f.context))

            assertThat(reason).contains("from 1 to 2")
        }

        @Test
        fun `a confirm after the phase moved to somebody who left the project is turned down`() {
            f.element(PathElementKind.PHASE, phaseId, owner = f.outsiderId)

            assertThat(action.recheck(f.json("phase_id" to phaseId), f.context)).contains("no longer on the path")
        }

        @Test
        fun `performing writes the stored change over the phase as it is now`() = runTest {
            current(phaseId, position = 4, title = "Renamed since", description = "Edited since")
            val request = slot<UpdateOnboardingPhaseRequest>()
            every { phaseService.updateOnboardingPhaseById(phaseId, capture(request)) } returns mockk(relaxed = true)

            action.perform(f.json("phase_id" to phaseId, "position" to 0), f.context)

            assertThat(request.captured).isEqualTo(UpdateOnboardingPhaseRequest(0, "Renamed since", "Edited since"))
        }
    }

    @Nested
    inner class Delete {
        private val action = DeletePhaseAction(f.scope, phaseService)
        private val phaseId = UUID.randomUUID()

        @Test
        fun `is destructive`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        }

        @Test
        fun `says what goes with the phase and how much progress it holds`() {
            f.element(
                PathElementKind.PHASE,
                phaseId,
                title = "Setup",
                contains = "4 steps and 9 tasks",
                finishedSteps = 2,
            )

            val draft = f.proposed(action.draft(f.call("delete_phase", "phase_id" to phaseId), f.context))

            assertThat(
                draft.preview,
            ).contains("Setup", "takes 4 steps and 9 tasks with it", "got through 2 of its steps")
            assertThat(draft.preview).contains("cannot be undone")
        }

        @Test
        fun `an empty phase makes no claim about what is inside`() {
            f.element(PathElementKind.PHASE, phaseId, title = "Empty")

            val draft = f.proposed(action.draft(f.call("delete_phase", "phase_id" to phaseId), f.context))

            assertThat(draft.preview).doesNotContain("takes")
            assertThat(draft.preview).doesNotContain("got through")
        }

        @Test
        fun `a phase on somebody else's path is refused at proposal and at confirm`() {
            f.element(PathElementKind.PHASE, phaseId, owner = f.outsiderId)

            assertThat(f.refusal(action.draft(f.call("delete_phase", "phase_id" to phaseId), f.context)))
                .contains("not on the onboarding path")
            assertThat(action.recheck(f.json("phase_id" to phaseId), f.context)).isNotNull()
        }

        @Test
        fun `performing deletes the phase by id`() = runTest {
            action.perform(f.json("phase_id" to phaseId), f.context)

            verify { phaseService.deleteOnboardingPhaseById(phaseId) }
        }
    }
}
