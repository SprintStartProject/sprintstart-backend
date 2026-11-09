package com.sprintstart.sprintstartbackend.path.assistant

import com.sprintstart.sprintstartbackend.assistant.external.text
import com.sprintstart.sprintstartbackend.blueprint.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.blueprint.external.enums.StepType
import com.sprintstart.sprintstartbackend.path.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.skip.GetOnboardingStepSkipResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class PhaseProgressTextTest {
    private val phaseId = UUID.randomUUID()

    private fun step(
        title: String,
        position: Int,
        status: StepStatus = StepStatus.IN_PROGRESS,
        blockers: Set<UUID> = emptySet(),
        pendingSkip: Boolean = false,
    ): GetOnboardingStepsResponse {
        val id = UUID.randomUUID()
        return GetOnboardingStepsResponse(
            id = id,
            phaseId = phaseId,
            position = position,
            title = title,
            description = "",
            type = StepType.TASK,
            estimatedMinutes = 20,
            isAiAssisted = false,
            status = status,
            completedAt = null,
            skip = if (pendingSkip) {
                GetOnboardingStepSkipResponse(
                    UUID.randomUUID(),
                    id,
                    "reason",
                    null,
                    null,
                    null,
                )
            } else {
                null
            },
            blockerIds = blockers,
        )
    }

    private fun question(text: String, status: QuestionStatus = QuestionStatus.OPEN, blockers: Set<UUID> = emptySet()) =
        GetOnboardingQuestionForUserResponse(
            id = UUID.randomUUID(),
            phaseId = phaseId,
            position = 0,
            type = CheckQuestionType.SHORT_TEXT,
            question = text,
            status = status,
            blockerIds = blockers,
        )

    private fun phase(
        title: String,
        steps: List<GetOnboardingStepsResponse> = emptyList(),
        questions: List<GetOnboardingQuestionForUserResponse> = emptyList(),
        blockers: Set<UUID> = emptySet(),
        id: UUID = phaseId,
    ) = GetOnboardingPhaseForUserResponse(
        id = id,
        pathId = UUID.randomUUID(),
        position = 0,
        title = title,
        description = "",
        locked = false,
        steps = steps,
        questions = questions,
        blockerIds = blockers,
    )

    private fun task(step: GetOnboardingStepsResponse, title: String, finished: Boolean) =
        GetOnboardingTaskResponse(UUID.randomUUID(), step.id, 0, title, "", finished)

    private fun text(block: StringBuilder.() -> Unit) = buildString(block)

    @Test
    fun `a step is ready to close when every line of a non-empty checklist is ticked`() {
        val done = step("Clone the repo", 1)
        val half = step("Run the tests", 2)
        val bare = step("Read the README", 3)
        val phase = phase("Arrive", listOf(half, done, bare))
        val checklists = mapOf(
            done.id to listOf(task(done, "clone", true), task(done, "build", true)),
            half.id to listOf(task(half, "unit", true), task(half, "e2e", false)),
            bare.id to emptyList(),
        )

        assertThat(readyToClose(phase, checklists)).containsExactly(done)
    }

    @Test
    fun `a step they asked to skip is not pushed to be closed`() {
        val skipping = step("Clone the repo", 1, pendingSkip = true)
        val phase = phase("Arrive", listOf(skipping))

        assertThat(readyToClose(phase, mapOf(skipping.id to listOf(task(skipping, "clone", true))))).isEmpty()
    }

    @Test
    fun `ready steps come in position order`() {
        val second = step("Second", 2)
        val first = step("First", 1)
        val phase = phase("Arrive", listOf(second, first))
        val checklists = mapOf(first.id to listOf(task(first, "a", true)), second.id to listOf(task(second, "b", true)))

        assertThat(readyToClose(phase, checklists)).containsExactly(first, second)
    }

    @Test
    fun `unlockedBy names the items in the phase that wait on the step`() {
        val step = step("Clone the repo", 1)
        val next = step("Run the tests", 2, status = StepStatus.WAITING, blockers = setOf(step.id))
        val check = question("Which branch do you start from?", blockers = setOf(step.id))
        val phase = phase("Arrive", listOf(step, next), listOf(check))
        val numbers = mapOf(step.id to 1, next.id to 2, check.id to 3)

        assertThat(unlockedBy(step, phase, listOf(phase), numbers))
            .containsExactly("#2 “Run the tests”", "#3 “Which branch do you start from?”")
    }

    @Test
    fun `the last open step of a phase also names the phases that wait on that phase`() {
        val step = step("Clone the repo", 1)
        val finished = step("Install", 2, status = StepStatus.FINISHED)
        val phase = phase("Arrive", listOf(step, finished), listOf(question("Done?", QuestionStatus.PASSED)))
        val after = phase("Ship", blockers = setOf(phaseId), id = UUID.randomUUID())
        val unrelated = phase("Learn", id = UUID.randomUUID())

        assertThat(unlockedBy(step, phase, listOf(phase, after, unrelated), emptyMap()))
            .containsExactly("the phase “Ship”")
    }

    @Test
    fun `a step with something else still open names no later phase`() {
        val step = step("Clone the repo", 1)
        val stillOpen = phase("Arrive", listOf(step, step("Install", 2, status = StepStatus.WAITING)))
        val questionOpen = phase("Arrive", listOf(step), listOf(question("Done?")))
        val after = phase("Ship", blockers = setOf(phaseId), id = UUID.randomUUID())

        assertThat(unlockedBy(step, stillOpen, listOf(stillOpen, after), emptyMap())).isEmpty()
        assertThat(unlockedBy(step, questionOpen, listOf(questionOpen, after), emptyMap())).isEmpty()
    }

    @Test
    fun `ready to close lists each step with its ids, link and what it holds up`() {
        val done = step("Clone the repo", 1)
        val next = step("Run the tests", 2, status = StepStatus.WAITING, blockers = setOf(done.id))
        val phase = phase("Arrive", listOf(done, next))

        val text =
            text { appendReadyToClose(phase, listOf(phase), mapOf(done.id to listOf(task(done, "clone", true)))) }

        assertThat(text).contains("READY TO CLOSE")
        assertThat(text).contains(
            "- #1 “Clone the repo” [step_id: ${done.id}] [link: /onboarding?step=${done.id}] " +
                "-- it is what #2 “Run the tests” waits on",
        )
        assertThat(text).contains("call complete_step")
    }

    @Test
    fun `a ready step that holds nothing up says only what it is`() {
        val done = step("Clone the repo", 1)
        val phase = phase("Arrive", listOf(done, step("Other", 2, status = StepStatus.WAITING)))

        val text =
            text { appendReadyToClose(phase, listOf(phase), mapOf(done.id to listOf(task(done, "clone", true)))) }

        assertThat(text).contains("[link: /onboarding?step=${done.id}]\n")
        assertThat(text).doesNotContain("-- it is what")
    }

    @Test
    fun `nothing is said about closing when no step is ready`() {
        val step = step("Clone the repo", 1)

        assertThat(text { appendReadyToClose(phase("Arrive", listOf(step)), emptyList(), emptyMap()) }).isEmpty()
    }

    @Test
    fun `the checklist of the current step marks each line and gives its id`() {
        val current = step("Run the tests", 1)
        val done = task(current, "unit", true)
        val open = task(current, "e2e", false)

        val text = text { appendCurrentTasks(current, mapOf(current.id to listOf(done, open))) }

        assertThat(text).contains("The checklist of “Run the tests”, the step they are on:")
        assertThat(text).contains("- [done] “unit” [task_id: ${done.id}]")
        assertThat(text).contains("- [open] “e2e” [task_id: ${open.id}]")
        assertThat(text).contains("Ticking these off is complete_task")
        assertThat(text).doesNotContain("more")
    }

    @Test
    fun `a long checklist shows twelve lines and counts the rest`() {
        val current = step("Run the tests", 1)
        val tasks = (1..15).map { task(current, "line $it", false) }

        val text = text { appendCurrentTasks(current, mapOf(current.id to tasks)) }

        assertThat(text.lines().count { it.startsWith("- [open]") }).isEqualTo(12)
        assertThat(text).contains("- and 3 more")
    }

    @Test
    fun `there is no checklist without a current step or without lines`() {
        val current = step("Run the tests", 1)

        assertThat(text { appendCurrentTasks(null, emptyMap()) }).isEmpty()
        assertThat(text { appendCurrentTasks(current, mapOf(current.id to emptyList())) }).isEmpty()
    }

    @Test
    fun `a ready step comes first as the next thing`() {
        val ready = step("Clone the repo", 1)

        val text = text { appendNextItem(phase("Arrive", listOf(ready)), ready) }

        assertThat(text).contains(
            "The next thing waiting for them: finishing the step “Clone the repo” [step_id: ${ready.id}] " +
                "[link: /onboarding?step=${ready.id}], whose checklist is already done.",
        )
    }

    @Test
    fun `without a ready step the next item is the phase's own next thing, or nothing`() {
        val started = step("Run the tests", 1)

        assertThat(text { appendNextItem(phase("Arrive", listOf(started)), null) })
            .contains("The next thing waiting for them: the step “Run the tests” [step_id: ${started.id}]")
        assertThat(text { appendNextItem(phase("Arrive", listOf(step("Done", 1, StepStatus.FINISHED))), null) })
            .contains("Nothing in this phase is open right now.")
    }
}
