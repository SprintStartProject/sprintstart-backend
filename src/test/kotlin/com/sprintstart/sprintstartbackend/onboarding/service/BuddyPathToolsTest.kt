package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.GenerationStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingGenerationIssueResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * What the mentor is told about the path the hire is walking.
 *
 * The through-line: **the mentor gets the phase they are standing in, and not the plan.** A path is
 * the one thing on this product that is already written down on a page in front of the hire, so a
 * tool that hands the model all sixteen phases produces a mentor that reads a table of contents out.
 * The other half is what the tool refuses to carry: no correct answers, and no blended progress
 * figure.
 */
class BuddyPathToolsTest {
    private val onboardingPathService: OnboardingPathService = mockk()

    // Checklists are read per step, so a phase with no started step never reaches it. Empty by
    // default: the cases that are about a checklist put one there.
    private val onboardingTaskService: OnboardingTaskService = mockk {
        every { getOnboardingTasksByStepId(any()) } returns emptyList()
    }
    private val tools = BuddyPathTools(onboardingPathService, onboardingTaskService)

    private val userId = UUID.randomUUID()

    // -- absent, never empty ----------------------------------------------------------------------

    @Test
    fun `a hire with no path is offered no path tool at all`() {
        every { onboardingPathService.hasPath(userId) } returns false

        assertThat(tools.toolSpecs(userId)).isEmpty()
        assertThat(tools.hasPath(userId)).isFalse()
    }

    @Test
    fun `a hire with a path is offered exactly the read tool`() {
        every { onboardingPathService.hasPath(userId) } returns true

        assertThat(tools.toolSpecs(userId).map { it.name }).containsExactly("get_my_onboarding_path")
    }

    @Test
    fun `the greeting says nothing at all about a path that does not exist`() {
        every { onboardingPathService.findPathForUserId(userId) } returns null

        assertThat(tools.snapshotFor(userId)).isNull()
    }

    @Test
    fun `called without a path, the tool still answers in a sentence`() {
        every { onboardingPathService.findPathForUserId(userId) } returns null

        // Never silence: a tool that answers with nothing is one the model fills in for itself.
        assertThat(tools.execute(userId)).contains("no onboarding path yet")
    }

    // -- the phase they are standing in -----------------------------------------------------------

    @Test
    fun `the current phase is the first with anything open, questions included`() {
        // Every step of phase two is done, so a steps-only rule would put the hire in phase three.
        // Its question has not been passed, which is what makes it still their phase.
        val path = path(
            phase(0, "Overview", steps = listOf(step("Read the wiki", StepStatus.FINISHED))),
            phase(
                1,
                "Meetings",
                steps = listOf(step("Sit in on a standup", StepStatus.FINISHED)),
                questions = listOf(question("Who runs the retro?", QuestionStatus.OPEN)),
            ),
            phase(2, "Deployment", steps = listOf(step("Ship something", StepStatus.WAITING))),
        )
        every { onboardingPathService.findPathForUserId(userId) } returns path

        val text = tools.execute(userId)

        assertThat(text).contains("standing in phase 2")
        assertThat(text).contains("Who runs the retro?")
        // The phase ahead is named, but only by title -- its step is not in the prompt.
        assertThat(text).contains("Deployment")
        assertThat(text).doesNotContain("Ship something")
    }

    @Test
    fun `the ids every path action needs are carried, for the current phase`() {
        val step = step("Set up the repo", StepStatus.WAITING)
        val question = question("What is a definition of done?", QuestionStatus.RETRY)
        val phase = phase(0, "Setup", steps = listOf(step), questions = listOf(question))
        every { onboardingPathService.findPathForUserId(userId) } returns path(phase)

        val text = tools.execute(userId)

        assertThat(text).contains("phase_id: ${phase.id}")
        assertThat(text).contains("step_id: ${step.id}")
        assertThat(text).contains("question_id: ${question.id}")
    }

    @Test
    fun `a question carries the options the hire sees and no verdict on them`() {
        val question = question(
            "Which meeting sets the sprint scope?",
            QuestionStatus.OPEN,
            options = listOf("Planning", "Retro"),
        )
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Meetings", questions = listOf(question)))

        val text = tools.execute(userId)

        assertThat(text).contains("Planning; Retro")
        // The hire-facing shape carries no correct flag, and nothing here invents one. The mentor
        // cannot leak an answer it was never given, which is the point of reading this shape.
        assertThat(text.lowercase()).doesNotContain("correct")
    }

    @Test
    fun `progress is phases behind them, never a blended figure`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "One", steps = listOf(step("a", StepStatus.FINISHED))),
            phase(1, "Two", steps = listOf(step("b", StepStatus.WAITING))),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("behind them")
        // What the system observed and what the hire ticked are different facts; a percentage over
        // the two is a number the mentor would repeat and nobody could act on.
        assertThat(text).doesNotContain("%")
    }

    @Test
    fun `one next thing is named, and it is the first unlocked open item`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(
                0,
                "Setup",
                steps = listOf(
                    step("Install the toolchain", StepStatus.FINISHED),
                    step("Clone the repository", StepStatus.WAITING),
                    step("Run the tests", StepStatus.WAITING),
                ),
            ),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("The next thing waiting for them: the step “Clone the repository”")
        assertThat(text.substringAfter("next thing waiting")).doesNotContain("Run the tests")
    }

    @Test
    fun `a locked phase is never where the next thing comes from`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "Gated", locked = true, steps = listOf(step("Deploy to prod", StepStatus.WAITING))),
            phase(1, "Open", steps = listOf(step("Read the runbook", StepStatus.WAITING))),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("next thing waiting for them: the step “Read the runbook”")
    }

    // -- what a hire and a mentor can both point at ------------------------------------------------

    @Test
    fun `items are numbered the way the hire's page numbers them`() {
        // Steps first in position order, then questions -- the order the page lists them in, which is
        // the whole point: a number only helps if "3" means the same item on both sides.
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(
                0,
                "Setup",
                steps = listOf(step("First", StepStatus.WAITING), step("Second", StepStatus.WAITING)),
                questions = listOf(question("Third?", QuestionStatus.OPEN)),
            ),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("#1 [open] “First”")
        assertThat(text).contains("#2 [open] “Second”")
        assertThat(text).contains("#3 [open] “Third?”")
    }

    @Test
    fun `every item carries the link that opens it`() {
        val step = step("Clone the repository", StepStatus.WAITING)
        val question = question("Who runs the retro?", QuestionStatus.OPEN)
        val phase = phase(0, "Setup", steps = listOf(step), questions = listOf(question))
        every { onboardingPathService.findPathForUserId(userId) } returns path(phase)

        val text = tools.execute(userId)

        assertThat(text).contains("link: /onboarding/${step.id}")
        assertThat(text).contains("link: /onboarding?question=${question.id}")
        assertThat(text).contains("link: /onboarding?phase=${phase.id}")
    }

    @Test
    fun `a locked step says what it waits on, by name`() {
        // Told only "locked", the mentor agreed a hire could go ahead with a step their own page
        // refuses to open. The blocker's title and number are the answer it should give instead.
        val blocker = step("Install the toolchain", StepStatus.WAITING)
        val blocked = step("Run the tests", StepStatus.WAITING, locked = true, blockers = setOf(blocker.id))
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(blocker, blocked)))

        val text = tools.execute(userId)

        assertThat(text).contains("LOCKED, cannot be started yet")
        assertThat(text).contains("waits on #1 “Install the toolchain”")
        assertThat(text).contains("Do not offer to start it")
    }

    @Test
    fun `a phase locked from outside says so once rather than on every line`() {
        val earlier = phase(0, "Overview", steps = listOf(step("Read the wiki", StepStatus.WAITING)))
        val later = phase(
            1,
            "Deployment",
            locked = true,
            steps = listOf(step("Ship something", StepStatus.WAITING, locked = true)),
        )
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(earlier, later.copy(blockerIds = setOf(earlier.id)))

        // Its own phase is the one being described, so select it by finishing the first.
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            earlier.copy(steps = listOf(step("Read the wiki", StepStatus.FINISHED))),
            later.copy(blockerIds = setOf(earlier.id)),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("This whole phase is locked")
        assertThat(text).contains("It waits on: “Overview”")
    }

    @Test
    fun `the checklist of the step they are on comes with it`() {
        val started = step("Clone the repository", StepStatus.IN_PROGRESS)
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(started)))
        every { onboardingTaskService.getOnboardingTasksByStepId(started.id) } returns listOf(
            task("Install git", finished = true),
            task("Clone it", finished = false),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("[done] “Install git”")
        assertThat(text).contains("[open] “Clone it”")
        assertThat(text).contains("complete_task")
        // The product allows a finished step with open lines, and the mentor must not invent a rule
        // it does not have.
        assertThat(text).contains("finished with lines still open")
    }

    @Test
    fun `a locked step's checklist is never the one put in front of the mentor`() {
        val locked = step("Deploy to staging", StepStatus.WAITING, locked = true)
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(locked)))

        tools.execute(userId)

        verify(exactly = 0) { onboardingTaskService.getOnboardingTasksByStepId(locked.id) }
    }

    // -- the empty-phase repair -------------------------------------------------------------------

    @Test
    fun `a phase that generated nothing is named, with what to do about it`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING))),
            issues = listOf(
                OnboardingGenerationIssueResponse(UUID.randomUUID(), "Deployment", GenerationStatus.SKIPPED),
            ),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("Deployment")
        assertThat(text).contains("SKIPPED")
        // Not the hire's fault, and the one part of a path a conversation can genuinely repair.
        assertThat(text).contains("not the hire's fault")
        assertThat(text).contains("add_path_step")
    }

    @Test
    fun `an empty current phase says so rather than listing nothing`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(phase(0, "Deployment"))

        val text = tools.execute(userId)

        assertThat(text).contains("nothing in it")
    }

    // -- the greeting -----------------------------------------------------------------------------

    @Test
    fun `the greeting names the phase and the next thing, and no tool`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING))),
        )

        val snapshot = tools.snapshotFor(userId)

        assertThat(snapshot).contains("phase 1 of 1")
        assertThat(snapshot).contains("Clone the repository")
        // A tool name in the greeting is a tool name in front of the hire; the opener holds none.
        assertThat(snapshot).doesNotContain("step_id")
        assertThat(snapshot).doesNotContain("get_my_onboarding_path")
    }

    @Test
    fun `a finished path is reported as finished rather than as its last phase`() {
        every { onboardingPathService.findPathForUserId(userId) } returns path(
            phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.FINISHED))),
        )

        assertThat(tools.snapshotFor(userId)).contains("finished every phase")
        assertThat(tools.execute(userId)).contains("every one of them is finished")
    }

    // -- lookups, which are also the authorization ------------------------------------------------

    @Test
    fun `a node that is not on the hire's own path is simply not found`() {
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING))))

        // Resolving through the hire's own path is what makes an id from anywhere else unusable.
        assertThat(tools.findStep(userId, UUID.randomUUID())).isNull()
        assertThat(tools.findQuestion(userId, UUID.randomUUID())).isNull()
        assertThat(tools.findPhase(userId, UUID.randomUUID())).isNull()
    }

    @Test
    fun `a node on the hire's own path is found with its own title`() {
        val step = step("Clone the repository", StepStatus.WAITING)
        val phase = phase(0, "Setup", steps = listOf(step))
        every { onboardingPathService.findPathForUserId(userId) } returns path(phase)

        assertThat(tools.findStep(userId, step.id)?.title).isEqualTo("Clone the repository")
        assertThat(tools.findPhase(userId, phase.id)?.title).isEqualTo("Setup")
    }

    // -- fixtures ---------------------------------------------------------------------------------

    private fun path(
        vararg phases: GetOnboardingPhaseForUserResponse,
        issues: List<OnboardingGenerationIssueResponse> = emptyList(),
    ) = GetOnboardingPathForUserResponse(
        id = UUID.randomUUID(),
        userId = userId,
        createdAt = Instant.EPOCH,
        phases = phases.toList(),
        generationIssues = issues,
    )

    private fun phase(
        position: Int,
        title: String,
        locked: Boolean = false,
        steps: List<GetOnboardingStepsResponse> = emptyList(),
        questions: List<GetOnboardingQuestionForUserResponse> = emptyList(),
    ) = GetOnboardingPhaseForUserResponse(
        id = UUID.randomUUID(),
        pathId = UUID.randomUUID(),
        position = position,
        title = title,
        description = "",
        locked = locked,
        steps = steps,
        questions = questions,
    )

    private var stepPosition = 0

    private fun step(
        title: String,
        status: StepStatus,
        locked: Boolean = false,
        blockers: Set<UUID> = emptySet(),
    ) = GetOnboardingStepsResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = stepPosition++,
        title = title,
        description = "",
        type = StepType.TASK,
        estimatedMinutes = 20,
        isAiAssisted = false,
        status = status,
        completedAt = null,
        skip = null,
        locked = locked,
        blockerIds = blockers,
    )

    private fun task(title: String, finished: Boolean) = GetOnboardingTaskResponse(
        id = UUID.randomUUID(),
        stepId = UUID.randomUUID(),
        position = 0,
        title = title,
        description = "",
        finished = finished,
    )

    private var questionPosition = 100

    private fun question(
        text: String,
        status: QuestionStatus,
        options: List<String> = emptyList(),
    ) = GetOnboardingQuestionForUserResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = questionPosition++,
        type = CheckQuestionType.MULTIPLE_CHOICE,
        question = text,
        options = options.mapIndexed { index, label ->
            QuestionOptionForUserResponse(id = UUID.randomUUID(), position = index, label = label)
        },
        status = status,
    )
}
