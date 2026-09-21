package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class TaskTeamActionsTest {
    private val f = ContentFixture()
    private val taskService: OnboardingTaskService = mockk(relaxed = true)

    private fun task(
        id: UUID = UUID.randomUUID(),
        stepId: UUID = UUID.randomUUID(),
        position: Int = 0,
        title: String = "Clone",
        description: String = "git clone",
        finished: Boolean = false,
    ) = GetOnboardingTaskResponse(id, stepId, position, title, description, finished)

    @Nested
    inner class Add {
        private val action = AddTaskAction(f.scope, taskService)
        private val stepId = UUID.randomUUID()

        private fun addCall(vararg extra: Pair<String, Any?>) =
            f.call("add_task", "step_id" to stepId, "title" to "Build", *extra)

        @Test
        fun `appends by default and starts unfinished`() {
            f.element(PathElementKind.STEP, stepId, title = "Install", children = 2, stepStatus = StepStatus.WAITING)

            val draft = f.proposed(action.draft(addCall(), f.context))

            assertThat(draft.params.text("position")).isEqualTo("2")
            assertThat(draft.preview).contains("“Install”", "place 3 of 3", "starts unfinished")
            assertThat(draft.preview).doesNotContain("reopens")
        }

        @Test
        fun `adding to a finished step says it reopens the step`() {
            f.element(PathElementKind.STEP, stepId, children = 1, stepStatus = StepStatus.FINISHED)

            val draft = f.proposed(action.draft(addCall(), f.context))

            assertThat(draft.preview).contains("already finished", "reopens it", "back to in progress for Sam Rivera")
        }

        @Test
        fun `a skipped step is not claimed to reopen`() {
            f.element(PathElementKind.STEP, stepId, children = 1, stepStatus = StepStatus.SKIPPED)

            val draft = f.proposed(action.draft(addCall(), f.context))

            assertThat(draft.preview).doesNotContain("reopens")
        }

        @Test
        fun `a step on another person's path is refused`() {
            f.element(PathElementKind.STEP, stepId, owner = f.outsiderId)

            val reason = f.refusal(action.draft(addCall(), f.context))

            assertThat(reason).contains("not on the onboarding path")
        }

        @Test
        fun `a blank title and an impossible place are refused`() {
            f.element(PathElementKind.STEP, stepId, children = 1)

            assertThat(f.refusal(action.draft(f.call("add_task", "step_id" to stepId, "title" to ""), f.context)))
                .contains("needs a title")
            assertThat(f.refusal(action.draft(addCall("place" to 5), f.context))).contains("from 1 to 2")
        }

        @Test
        fun `a confirm after the step lost tasks is turned down`() {
            f.element(PathElementKind.STEP, stepId, children = 0)

            assertThat(action.recheck(f.json("step_id" to stepId, "position" to 3), f.context))
                .contains("fewer tasks")
        }

        @Test
        fun `performing creates the task with what was stored`() =
            runTest {
                val request = slot<CreateOnboardingTaskRequest>()
                every { taskService.createOnboardingTaskForStepId(stepId, capture(request)) } returns
                    mockk(relaxed = true)

                action.perform(
                    f.json("step_id" to stepId, "title" to "Build", "description" to "d", "position" to 1),
                    f.context,
                )

                assertThat(request.captured).isEqualTo(CreateOnboardingTaskRequest(1, "Build", "d"))
            }
    }

    @Nested
    inner class Update {
        private val action = UpdateTaskAction(f.scope, taskService)
        private val taskId = UUID.randomUUID()
        private val stepId = UUID.randomUUID()

        private fun existing(finished: Boolean) {
            f.element(PathElementKind.TASK, taskId)
            every { taskService.getOnboardingTaskById(taskId) } returns
                task(taskId, stepId, position = 1, finished = finished)
            every { taskService.getOnboardingTasksByStepId(stepId) } returns (0 until 3).map { task(position = it) }
        }

        @Test
        fun `previews the change and says the tick stays as it is`() {
            existing(finished = true)

            val draft = f.proposed(
                action.draft(f.call("update_task", "task_id" to taskId, "title" to "Clone the repo"), f.context),
            )

            assertThat(draft.preview).contains("“Clone” becomes “Clone the repo”", "stays as it is (done)")
        }

        @Test
        fun `nothing new is refused`() {
            existing(finished = false)

            val reason = f.refusal(
                action.draft(f.call("update_task", "task_id" to taskId, "title" to "Clone"), f.context),
            )

            assertThat(reason).contains("Nothing would change")
        }

        @Test
        fun `there is no way to ask for a tick`() {
            val properties = action.spec.parameters["properties"].toString()

            assertThat(properties).doesNotContain("finished").doesNotContain("done")
        }

        @Test
        fun `performing passes the tick through exactly as the task has it now`() =
            runTest {
                every { taskService.getOnboardingTaskById(taskId) } returns task(taskId, stepId, finished = true)
                val request = slot<UpdateOnboardingTaskRequest>()
                every { taskService.updateOnboardingTaskById(taskId, capture(request)) } returns
                    mockk(relaxed = true)

                action.perform(f.json("task_id" to taskId, "title" to "Renamed"), f.context)

                assertThat(request.captured)
                    .isEqualTo(UpdateOnboardingTaskRequest(0, "Renamed", "git clone", finished = true))
            }

        @Test
        fun `a task on another person's path is refused at proposal and at confirm`() {
            f.element(PathElementKind.TASK, taskId, owner = f.outsiderId)

            assertThat(f.refusal(action.draft(f.call("update_task", "task_id" to taskId, "title" to "x"), f.context)))
                .contains("not on the onboarding path")
            assertThat(action.recheck(f.json("task_id" to taskId), f.context)).isNotNull()
        }
    }

    @Nested
    inner class Delete {
        private val action = DeleteTaskAction(f.scope, taskService)
        private val taskId = UUID.randomUUID()

        @Test
        fun `is destructive and says it cannot be undone`() {
            f.element(PathElementKind.TASK, taskId, title = "Clone")

            val draft = f.proposed(action.draft(f.call("delete_task", "task_id" to taskId), f.context))

            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
            assertThat(draft.preview).contains("“Clone”", "cannot be undone")
        }

        @Test
        fun `performing deletes the task by id`() =
            runTest {
                action.perform(f.json("task_id" to taskId), f.context)

                verify { taskService.deleteOnboardingTaskById(taskId) }
            }
    }
}
