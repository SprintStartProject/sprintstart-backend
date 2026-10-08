package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class StepTeamActionsTest {
    private val f = ContentFixture()
    private val stepService: OnboardingStepService = mockk(relaxed = true)

    private fun step(
        id: UUID = UUID.randomUUID(),
        phaseId: UUID = UUID.randomUUID(),
        position: Int = 1,
        title: String = "Install",
        description: String = "Do it",
        type: StepType = StepType.TASK,
        minutes: Int = 20,
        outcome: String = "It runs",
        status: StepStatus = StepStatus.IN_PROGRESS,
    ) = GetOnboardingStepResponse(
        id = id,
        phaseId = phaseId,
        position = position,
        title = title,
        description = description,
        type = type,
        estimatedMinutes = minutes,
        isAiAssisted = false,
        expectedOutcomes = listOf(outcome),
        tasks = emptyList(),
        resources = emptyList(),
        status = status,
        completedAt = null,
        skip = null,
    )

    @Nested
    inner class Add {
        private val action = AddStepAction(f.scope, stepService)
        private val phaseId = UUID.randomUUID()

        private fun inPhaseOf(steps: Int) {
            f.element(PathElementKind.PHASE, phaseId, title = "Setup", children = steps)
        }

        private fun addCall(vararg extra: Pair<String, Any?>) =
            f.call("add_step", "phase_id" to phaseId, "title" to "Run tests", "type" to "task", *extra)

        @Test
        fun `appends by default, and says it starts waiting`() {
            inPhaseOf(2)

            val draft = f.proposed(action.draft(addCall("estimated_minutes" to 30), f.context))

            assertThat(draft.params.text("position")).isEqualTo("2")
            assertThat(draft.params.text("type")).isEqualTo("TASK")
            assertThat(draft.preview).contains("“Setup”", "about 30 min", "place 3 of 3", "starts waiting")
        }

        @Test
        fun `minutes default when left out`() {
            inPhaseOf(0)

            val draft = f.proposed(action.draft(addCall(), f.context))

            assertThat(draft.params.text("estimated_minutes")).isEqualTo("15")
        }

        @Test
        fun `a type outside the set is refused, naming the ones there are`() {
            inPhaseOf(0)

            val reason = f.refusal(
                action.draft(f.call("add_step", "phase_id" to phaseId, "title" to "x", "type" to "QUIZ"), f.context),
            )

            assertThat(reason).contains("VIDEO", "DOCUMENT", "TASK")
        }

        @Test
        fun `minutes that are not a positive whole number are refused`() {
            inPhaseOf(0)

            val reason = f.refusal(action.draft(addCall("estimated_minutes" to "soon"), f.context))

            assertThat(reason).contains("whole number of minutes")
        }

        @Test
        fun `a phase on another person's path is refused`() {
            f.element(PathElementKind.PHASE, phaseId, owner = f.outsiderId)

            val reason = f.refusal(action.draft(addCall(), f.context))

            assertThat(reason).contains("not on the onboarding path of anybody on this project")
        }

        @Test
        fun `a confirm after the phase lost steps is turned down`() {
            inPhaseOf(1)

            val reason = action.recheck(f.json("phase_id" to phaseId, "position" to 4), f.context)

            assertThat(reason).contains("fewer steps")
        }

        @Test
        fun `performing creates the step with what was stored`() =
            runTest {
                val request = slot<CreateOnboardingStepRequest>()
                every { stepService.createOnboardingStepForPhaseId(phaseId, capture(request)) } returns
                    mockk(relaxed = true)

                action.perform(
                    f.json(
                        "phase_id" to phaseId,
                        "title" to "Run tests",
                        "description" to "d",
                        "type" to "TASK",
                        "estimated_minutes" to 30,
                        "expected_outcome" to "green",
                        "position" to 1,
                    ),
                    f.context,
                )

                assertThat(request.captured)
                    .isEqualTo(CreateOnboardingStepRequest(1, "Run tests", "d", StepType.TASK, 30, "green"))
            }
    }

    @Nested
    inner class Update {
        private val action = UpdateStepAction(f.scope, stepService)
        private val stepId = UUID.randomUUID()
        private val phaseId = UUID.randomUUID()

        private fun existing(status: StepStatus = StepStatus.IN_PROGRESS) {
            f.element(PathElementKind.STEP, stepId, title = "Install")
            every { stepService.getOnboardingStepById(stepId) } returns
                step(id = stepId, phaseId = phaseId, status = status)
            every { stepService.getOnboardingStepsByPhaseId(phaseId) } returns
                (0 until 3).map { step(position = it) }
        }

        private fun updateCall(vararg args: Pair<String, Any?>) = f.call("update_step", "step_id" to stepId, *args)

        @Test
        fun `previews each change and says the status is not touched`() {
            existing()

            val draft = f.proposed(
                action.draft(
                    updateCall("type" to "video", "estimated_minutes" to 45, "expected_outcome" to ""),
                    f.context,
                ),
            )

            assertThat(draft.params.text("type")).isEqualTo("VIDEO")
            assertThat(draft.preview).contains("task becomes video", "20 min becomes 45 min")
            assertThat(draft.preview).contains("Expected outcome becomes: (empty)", "status stays in progress")
        }

        @Test
        fun `nothing that differs is refused as no change`() {
            existing()

            val reason = f.refusal(
                action.draft(updateCall("title" to "Install", "type" to "TASK", "estimated_minutes" to 20), f.context),
            )

            assertThat(reason).contains("Nothing would change")
        }

        @Test
        fun `a bad type, length or place is refused`() {
            existing()

            assertThat(f.refusal(action.draft(updateCall("type" to "QUIZ"), f.context)))
                .contains("Type must be one of")
            assertThat(f.refusal(action.draft(updateCall("estimated_minutes" to 0), f.context)))
                .contains("above zero")
            assertThat(f.refusal(action.draft(updateCall("place" to 8), f.context)))
                .contains("from 1 to 3")
        }

        @Test
        fun `a step on another person's path is refused at proposal and at confirm`() {
            f.element(PathElementKind.STEP, stepId, owner = f.outsiderId)

            assertThat(f.refusal(action.draft(updateCall("title" to "x"), f.context)))
                .contains("not on the onboarding path")
            assertThat(action.recheck(f.json("step_id" to stepId), f.context)).isNotNull()
        }

        @Test
        fun `performing changes the stored fields and carries the rest over as they are now`() =
            runTest {
                every { stepService.getOnboardingStepById(stepId) } returns
                    step(
                        id = stepId,
                        title = "Renamed since",
                        description = "Edited since",
                        minutes = 25,
                        outcome = "Now green",
                    )
                val request = slot<UpdateOnboardingStepRequest>()
                every { stepService.updateOnboardingStepById(stepId, capture(request)) } returns
                    mockk(relaxed = true)

                action.perform(f.json("step_id" to stepId, "estimated_minutes" to 40), f.context)

                assertThat(request.captured).isEqualTo(
                    UpdateOnboardingStepRequest(1, "Renamed since", "Edited since", StepType.TASK, 40, "Now green"),
                )
            }
    }

    @Nested
    inner class Delete {
        private val action = DeleteStepAction(f.scope, stepService)
        private val stepId = UUID.randomUUID()

        @Test
        fun `is destructive`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        }

        @Test
        fun `says what goes with it, and that a finished step's progress goes too`() {
            f.element(
                PathElementKind.STEP,
                stepId,
                title = "Install",
                contains = "3 tasks and 1 resource",
                stepStatus = StepStatus.FINISHED,
            )

            val draft = f.proposed(action.draft(f.call("delete_step", "step_id" to stepId), f.context))

            assertThat(draft.preview).contains("takes 3 tasks and 1 resource with it", "already finished it")
        }

        @Test
        fun `says so when the person is working on it right now`() {
            f.element(PathElementKind.STEP, stepId, stepStatus = StepStatus.IN_PROGRESS)

            val draft = f.proposed(action.draft(f.call("delete_step", "step_id" to stepId), f.context))

            assertThat(draft.preview).contains("Sam Rivera is working on it right now")
        }

        @Test
        fun `says the later steps move up only when there are later steps`() {
            f.element(PathElementKind.STEP, stepId, position = 0, siblings = 2)
            assertThat(f.proposed(action.draft(f.call("delete_step", "step_id" to stepId), f.context)).preview)
                .contains("The steps after it move up one place")

            f.element(PathElementKind.STEP, stepId, position = 1, siblings = 2)
            assertThat(f.proposed(action.draft(f.call("delete_step", "step_id" to stepId), f.context)).preview)
                .doesNotContain("move up")
        }

        @Test
        fun `a waiting step makes no claim about progress`() {
            f.element(PathElementKind.STEP, stepId, stepStatus = StepStatus.WAITING)

            val draft = f.proposed(action.draft(f.call("delete_step", "step_id" to stepId), f.context))

            assertThat(draft.preview).doesNotContain("working on it").doesNotContain("already")
        }

        @Test
        fun `performing deletes the step by id`() =
            runTest {
                action.perform(f.json("step_id" to stepId), f.context)

                verify { stepService.deleteOnboardingStepById(stepId) }
            }
    }
}
