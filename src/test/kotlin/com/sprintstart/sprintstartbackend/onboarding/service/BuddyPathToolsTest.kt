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
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingStepSkipResponse
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

        assertThat(text).contains("link: /onboarding?step=${step.id}")
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
        assertThat(text).contains("comes after #1 “Install the toolchain”, which is not finished yet")
        // And the edge the other way round, so the mentor knows what finishing #1 opens.
        assertThat(text).contains("· opens: #2")
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
    fun `a step whose checklist is done but that is still open is named, with what it holds up`() {
        // Every line ticked, the step's own button never pressed: whatever waits on it stays locked
        // and the hire has no idea why. The mentor has to raise it, and has to be able to say why.
        val done = step("Clone the repository", StepStatus.IN_PROGRESS)
        val waiting = step("Run the tests", StepStatus.WAITING, locked = true, blockers = setOf(done.id))
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(done, waiting)))
        every { onboardingTaskService.getOnboardingTasksByStepId(done.id) } returns listOf(
            task("Install git", finished = true),
            task("Clone it", finished = true),
        )

        val text = tools.execute(userId)

        assertThat(text).contains("READY TO CLOSE")
        assertThat(text).contains("#1 “Clone the repository” [step_id: ${done.id}]")
        assertThat(text).contains("it is what #2 “Run the tests” waits on")
        assertThat(text).contains("call complete_step for it in that same reply")
        // Where they are first, then what it opens, then the question: never the button first.
        assertThat(text).contains("do not lead with the button")
        assertThat(text).contains("The next thing waiting for them: finishing the step “Clone the repository”")
    }

    @Test
    fun `a step with open lines, or with no checklist at all, is not ready to close`() {
        // No checklist is not a done checklist: there is nothing that says the work happened.
        val partly = step("Clone the repository", StepStatus.IN_PROGRESS)
        val bare = step("Read the handbook", StepStatus.WAITING)
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(partly, bare)))
        every { onboardingTaskService.getOnboardingTasksByStepId(partly.id) } returns listOf(
            task("Install git", finished = true),
            task("Clone it", finished = false),
        )

        assertThat(tools.execute(userId)).doesNotContain("READY TO CLOSE")
    }

    @Test
    fun `closing the last open step of a phase names the phases waiting on it`() {
        val done = step("Clone the repository", StepStatus.IN_PROGRESS)
        val setup = phase(0, "Setup", steps = listOf(done))
        val next = phase(1, "First change", locked = true).copy(blockerIds = setOf(setup.id))
        every { onboardingPathService.findPathForUserId(userId) } returns path(setup, next)
        every { onboardingTaskService.getOnboardingTasksByStepId(done.id) } returns
            listOf(task("Clone it", finished = true))

        assertThat(tools.execute(userId)).contains("it is what the phase “First change” waits on")
    }

    @Test
    fun `the greeting opens on a step that is done but never closed`() {
        val done = step("Clone the repository", StepStatus.IN_PROGRESS)
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(done)))
        every { onboardingTaskService.getOnboardingTasksByStepId(done.id) } returns
            listOf(task("Clone it", finished = true))

        val snapshot = tools.snapshotFor(userId)

        assertThat(snapshot).contains("Every line of the checklist of “Clone the repository” is ticked")
        // Addressed to a greeting, which holds no tools: no tool name in front of the hire.
        assertThat(snapshot).doesNotContain("complete_step")
    }

    @Test
    fun `a question answered wrong suggests a refresher step, and never the answer`() {
        val missed = question("Who runs the retro?", QuestionStatus.RETRY, options = listOf("The SM", "The PO"))
        val setup = phase(0, "Meetings", questions = listOf(missed))
        every { onboardingPathService.findPathForUserId(userId) } returns path(setup)

        val text = tools.execute(userId)

        assertThat(text).contains("they got this wrong before")
        assertThat(text).contains("add_path_step for one short refresher step in this phase [phase_id: ${setup.id}]")
        assertThat(text).contains("never the answer")
    }

    @Test
    fun `the phase is described as a graph, with what each item opens and what is open now`() {
        // Told only about locks, the mentor read the numbers as a sequence -- "after #6 comes #7" --
        // about items that did not depend on each other at all.
        val first = step("Install the toolchain", StepStatus.FINISHED)
        val left = step("Run the tests", StepStatus.WAITING, blockers = setOf(first.id))
        val right = step("Read the style guide", StepStatus.WAITING, blockers = setOf(first.id))
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(first, left, right)))

        val text = tools.execute(userId)

        assertThat(text).contains("dependency graph, not a list")
        assertThat(text).contains("· opens: #2, #3")
        assertThat(text).contains("Open right now: #2, #3")
    }

    @Test
    fun `the next thing follows the page's rule, a step before a question`() {
        // Steps and questions carry separate positions; mixing them by position named a question as
        // next while the page's own button pointed at a step.
        val question = question("Who runs the retro?", QuestionStatus.RETRY)
        val step = step("Clone the repository", StepStatus.WAITING).copy(position = 500)
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(step), questions = listOf(question)))

        assertThat(tools.execute(userId)).contains("The next thing waiting for them: the step “Clone the repository”")
    }

    @Test
    fun `a refresher for a missed question is placed in front of that question`() {
        val before = step("Read the retro guide", StepStatus.FINISHED)
        val missed = question("Who runs the retro?", QuestionStatus.RETRY).copy(blockerIds = setOf(before.id))
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Meetings", steps = listOf(before), questions = listOf(missed)))

        val text = tools.execute(userId)

        assertThat(text).contains("unlocks = [${missed.id}], waits_on = [${before.id}]")
    }

    @Test
    fun `adding a step as the next thing is spelled out with both halves`() {
        // Told how to place a step, the mentor passed what it unlocks and left out what it comes
        // after. So the path read hands over both, worked out from where the hire is.
        val current = step("Read the runbook", StepStatus.IN_PROGRESS)
        val next = step("Deploy to staging", StepStatus.WAITING, locked = true, blockers = setOf(current.id))
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Deployment", steps = listOf(current, next)))

        assertThat(tools.execute(userId))
            .contains("pass BOTH waits_on = [${current.id}] and unlocks = [${next.id}]")
    }

    @Test
    fun `a refresher in front of a question nothing leads into opens after where the hire is`() {
        val finished = step("Read the retro guide", StepStatus.FINISHED).copy(completedAt = Instant.EPOCH)
        val missed = question("Who runs the retro?", QuestionStatus.RETRY)
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Meetings", steps = listOf(finished), questions = listOf(missed)))

        assertThat(tools.execute(userId)).contains("unlocks = [${missed.id}], waits_on = [${finished.id}]")
    }

    @Test
    fun `the options of a question are never narrowed down`() {
        val q = question("Which meeting sets the scope?", QuestionStatus.RETRY, options = listOf("Planning", "Retro"))
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Meetings", questions = listOf(q)))

        // It told a hire one option "matches the title of #1 word for word": no answer stated, and the
        // question given away all the same.
        assertThat(tools.execute(userId)).contains("never narrow these down for them")
    }

    @Test
    fun `a step waiting on a skip decision is marked, and is never the next thing`() {
        // Pushing a hire to do a step they asked to skip, or to finish it -- which withdraws the
        // request -- is the mentor overruling a question that is their PM's to answer.
        val asked = step("Set up the VPN", StepStatus.WAITING).copy(skip = skip(accepted = null))
        val after = step("Read the handbook", StepStatus.WAITING)
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(asked, after)))
        every { onboardingTaskService.getOnboardingTasksByStepId(asked.id) } returns
            listOf(task("Install the client", finished = true))

        val text = tools.execute(userId)

        assertThat(text).contains("[SKIP REQUESTED, waiting on their PM] “Set up the VPN”")
        assertThat(text).contains("finishing the step withdraws the request")
        assertThat(text).contains("[page: /onboarding/${asked.id}]")
        assertThat(text).contains("The next thing waiting for them: the step “Read the handbook”")
        // Its checklist is done, but closing it would withdraw the request: not ready to close.
        assertThat(text).doesNotContain("READY TO CLOSE")
    }

    @Test
    fun `a declined skip carries the PM's comment`() {
        val declined = step("Set up the VPN", StepStatus.WAITING)
            .copy(skip = skip(accepted = false, reviewComment = "Everyone needs the company VPN."))
        every { onboardingPathService.findPathForUserId(userId) } returns
            path(phase(0, "Setup", steps = listOf(declined)))

        val text = tools.execute(userId)

        assertThat(text).contains("their PM declined it")
        assertThat(text).contains("their PM's comment on it: “Everyone needs the company VPN.”")
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

    private fun skip(accepted: Boolean?, reviewComment: String? = null) = GetOnboardingStepSkipResponse(
        id = UUID.randomUUID(),
        stepId = UUID.randomUUID(),
        reason = "I already know this.",
        accepted = accepted,
        reviewComment = reviewComment,
        reviewedAt = null,
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
