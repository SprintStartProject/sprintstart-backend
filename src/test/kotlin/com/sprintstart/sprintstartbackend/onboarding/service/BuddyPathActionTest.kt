package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.SubmitQuestionAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingStepSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * The actions that let a conversation move somebody along their onboarding path.
 *
 * The through-line, and the reason each of these is a *proposal*: **the mentor may say what it
 * thinks, and the hire is the one who changes their own onboarding.** Three rules follow, and every
 * case here is an instance of one of them:
 *
 * 1. Nothing is written until a confirm arrives, so proposing must not touch a service that writes.
 * 2. Every precondition is checked before the button, so a hire never clicks one and is told no.
 * 3. A refusal is addressed to the mentor and says what to do instead -- a tool result that only
 *    says "no" is one the model relays to the hire as a broken feature.
 */
class BuddyPathActionTest {
    private val buddyPathTools: BuddyPathTools = mockk()
    private val onboardingStepService: OnboardingStepService = mockk()
    private val onboardingTaskService: OnboardingTaskService = mockk()
    private val questionAttemptService: QuestionAttemptService = mockk()
    private val onboardingSkipService: OnboardingSkipService = mockk()
    private val userApi: UserApi = mockk()

    // The real path component behind a real action service: these cases are about the path actions
    // *and* about BuddyActionService routing them around the project gate, and mocking the component
    // would test the routing against nothing.
    private val pathActions = BuddyPathActions(
        buddyPathTools = buddyPathTools,
        onboardingStepService = onboardingStepService,
        onboardingTaskService = onboardingTaskService,
        questionAttemptService = questionAttemptService,
        onboardingSkipService = onboardingSkipService,
        userApi = userApi,
    )

    private val service = BuddyActionService(
        taskZeroService = mockk(relaxed = true),
        taskOrientationService = mockk(relaxed = true),
        knowledgeBaseService = mockk(relaxed = true),
        userGoalService = mockk(relaxed = true),
        userApi = userApi,
        attestationService = mockk(relaxed = true),
        boardService = mockk(relaxed = true),
        competencyPlacementService = mockk(relaxed = true),
        buddyPathActions = pathActions,
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth|hire"
    private val jwt: Jwt = mockk<Jwt>().also { every { it.subject } returns authId }

    // -- complete_step ----------------------------------------------------------------------------

    @Test
    fun `completing a step without an id sends the mentor to the read tool`() {
        val outcome = service.propose(call("complete_step"), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("get_my_onboarding_path")
    }

    @Test
    fun `a step that is not on the hire's own path cannot be ticked off`() {
        val strangerStep = UUID.randomUUID()
        every { buddyPathTools.findStep(userId, strangerStep) } returns null

        val outcome = service.propose(call("complete_step", "step_id" to strangerStep.toString()), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("not theirs")
    }

    @Test
    fun `every refusal says out loud that no button was shown`() {
        // It had been telling hires to confirm something that was never rendered. From inside the
        // model a refusal written as advice reads a lot like the offer having been made.
        val done = step("Clone the repository", StepStatus.FINISHED)
        every { buddyPathTools.findStep(userId, done.id) } returns done

        val outcome = service.propose(call("complete_step", "step_id" to done.id.toString()), userId)

        assertThat(outcome.toolResult).startsWith("NOT PROPOSED")
        assertThat(outcome.toolResult).contains("do not tell them to confirm anything")
    }

    @Test
    fun `a step that is already done is not offered again`() {
        val step = step("Clone the repository", StepStatus.FINISHED)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("complete_step", "step_id" to step.id.toString()), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("already done")
    }

    @Test
    fun `a locked step is explained rather than offered`() {
        val step = step("Deploy to staging", StepStatus.WAITING, locked = true)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("complete_step", "step_id" to step.id.toString()), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("is locked")
    }

    @Test
    fun `the button names the step, and proposing completes nothing`() {
        val step = step("Clone the repository", StepStatus.IN_PROGRESS)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("complete_step", "step_id" to step.id.toString()), userId)

        assertThat(outcome.proposal?.label).isEqualTo("Mark “Clone the repository” as done")
        assertThat(outcome.proposal?.stepId).isEqualTo(step.id)
        // "Ask; do not announce" is in the tool result too, because the model reads that and not
        // this test -- and because only the hire knows whether the work is actually done.
        assertThat(outcome.toolResult).contains("never say that it is done")
        verify(exactly = 0) { onboardingStepService.completeOnboardingStepForMe(any(), any()) }
    }

    @Test
    fun `a confirmed completion goes through the hire's own endpoint`() = runTest {
        val step = step("Clone the repository", StepStatus.IN_PROGRESS)
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findStep(userId, step.id) } returns step
        every { onboardingStepService.completeOnboardingStepForMe(authId, step.id) } returns
            completed("Clone the repository")

        val result = service.perform(BuddyActionRequest(action = "complete_step", stepId = step.id), jwt)

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Clone the repository")
        // A path belongs to a person, so no project is resolved -- a hire onboarding on two
        // projects, or on none yet, still has exactly one path.
        verify(exactly = 0) { userApi.getUsersByIds(any()) }
    }

    @Test
    fun `a step that became locked since the button was shown is not completed`() = runTest {
        // The completion route does not check locks -- the page enforces them by never offering the
        // button -- so a proposal clicked after the path around it changed has to be caught here.
        val step = step("Deploy to staging", StepStatus.WAITING, locked = true)
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val result = service.perform(BuddyActionRequest(action = "complete_step", stepId = step.id), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("locked")
        verify(exactly = 0) { onboardingStepService.completeOnboardingStepForMe(any(), any()) }
    }

    // -- complete_task ----------------------------------------------------------------------------

    @Test
    fun `a line of a step on the hire's path can be ticked off on its own`() {
        // The finer claim, and the reason both exist: "I have done the first two" is not a finished
        // step, and a mentor holding only complete_step would either overstate it or drop it.
        val step = step("Clone the repository", StepStatus.IN_PROGRESS)
        val task = task("Install git", finished = false, stepId = step.id)
        every { onboardingTaskService.getOnboardingTaskById(task.id) } returns task
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("complete_task", "task_id" to task.id.toString()), userId)

        assertThat(outcome.proposal?.label).isEqualTo("Tick off “Install git”")
        assertThat(outcome.proposal?.onboardingTaskId).isEqualTo(task.id)
        verify(exactly = 0) { onboardingTaskService.updateOnboardingTaskForMe(any(), any(), any()) }
    }

    @Test
    fun `a line whose step is not on the hire's path is not theirs to tick`() {
        // Ownership is the step's: the task read is by id, and this is what makes that safe.
        val task = task("Install git", finished = false, stepId = UUID.randomUUID())
        every { onboardingTaskService.getOnboardingTaskById(task.id) } returns task
        every { buddyPathTools.findStep(userId, task.stepId) } returns null

        val outcome = service.propose(call("complete_task", "task_id" to task.id.toString()), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).startsWith("NOT PROPOSED")
    }

    @Test
    fun `a line already ticked off is not offered again`() {
        val step = step("Clone the repository", StepStatus.IN_PROGRESS)
        val task = task("Install git", finished = true, stepId = step.id)
        every { onboardingTaskService.getOnboardingTaskById(task.id) } returns task
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("complete_task", "task_id" to task.id.toString()), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("already ticked off")
    }

    @Test
    fun `a confirmed tick writes the line back with everything else unchanged`() = runTest {
        val task = task("Install git", finished = false, stepId = UUID.randomUUID())
        every { onboardingTaskService.getOnboardingTaskForMe(authId, task.id) } returns task
        val written = slot<UpdateOnboardingTaskRequest>()
        every {
            onboardingTaskService.updateOnboardingTaskForMe(authId, task.id, capture(written))
        } returns updatedTask(task.title)

        val result = service.perform(
            BuddyActionRequest(action = "complete_task", onboardingTaskId = task.id),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(written.captured.finished).isTrue()
        // The mentor is changing one tick box, not editing the hire's checklist.
        assertThat(written.captured.title).isEqualTo(task.title)
        assertThat(written.captured.position).isEqualTo(task.position)
        // And it must not imply the step is now done, because it is not.
        assertThat(result.message).contains("still yours to finish")
    }

    // -- answer_question --------------------------------------------------------------------------

    @Test
    fun `an answer the hire did not give is refused before the button`() {
        val question = question("Who runs the retro?", QuestionStatus.OPEN)
        every { buddyPathTools.findQuestion(userId, question.id) } returns question

        val outcome = service.propose(
            call("answer_question", "question_id" to question.id.toString(), "answer" to "  "),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("never answer it for them")
    }

    @Test
    fun `a question already passed is not asked again`() {
        val question = question("Who runs the retro?", QuestionStatus.PASSED)
        every { buddyPathTools.findQuestion(userId, question.id) } returns question

        val outcome = service.propose(
            call("answer_question", "question_id" to question.id.toString(), "answer" to "the SM"),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("already passed")
    }

    @Test
    fun `an answer matching no option comes back with the options, so the mentor can ask again`() {
        val question = question(
            "Which meeting sets the sprint scope?",
            QuestionStatus.OPEN,
            options = listOf("Sprint planning", "Retro"),
        )
        every { buddyPathTools.findQuestion(userId, question.id) } returns question

        val outcome = service.propose(
            call("answer_question", "question_id" to question.id.toString(), "answer" to "the daily"),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("Sprint planning; Retro")
    }

    @Test
    fun `the hire's words are matched to an option, and the button shows which`() {
        val question = question(
            "Which meeting sets the sprint scope?",
            QuestionStatus.OPEN,
            options = listOf("Sprint planning", "Retro"),
        )
        every { buddyPathTools.findQuestion(userId, question.id) } returns question

        val outcome = service.propose(
            call("answer_question", "question_id" to question.id.toString(), "answer" to "planning"),
            userId,
        )

        assertThat(outcome.proposal?.label).isEqualTo("Send this answer: “Sprint planning”")
        // The answer is carried as the hire said it and re-matched at confirm time, so the option
        // that is recorded is the one whose label they read on the button.
        assertThat(outcome.proposal?.answer).isEqualTo("planning")
        assertThat(outcome.toolResult).contains("do not tell them whether it is correct")
        verify(exactly = 0) { questionAttemptService.submitQuestionAttemptForMe(any(), any(), any()) }
    }

    @Test
    fun `a confirmed answer records the option the label named`() = runTest {
        val question = question(
            "Which meeting sets the sprint scope?",
            QuestionStatus.OPEN,
            options = listOf("Sprint planning", "Retro"),
        )
        val planning = question.options.first { it.label == "Sprint planning" }
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findQuestion(userId, question.id) } returns question
        val submitted = slot<SubmitQuestionAttemptRequest>()
        every {
            questionAttemptService.submitQuestionAttemptForMe(authId, question.id, capture(submitted))
        } returns graded(correct = true, explanation = "Scope is agreed in planning.")

        val result = service.perform(
            BuddyActionRequest(action = "answer_question", questionId = question.id, answer = "planning"),
            jwt,
        )

        assertThat(submitted.captured.selectedOptionIds).containsExactly(planning.id)
        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("That was right")
        // The submit response is where the product reveals the explanation, on the page as well as
        // here; withholding it would make the conversation the worse place to answer.
        assertThat(result.message).contains("Scope is agreed in planning.")
    }

    @Test
    fun `a wrong answer is a recorded attempt, not a failed action`() = runTest {
        val question = question("Who runs the retro?", QuestionStatus.OPEN, options = listOf("The SM", "The PO"))
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findQuestion(userId, question.id) } returns question
        every { questionAttemptService.submitQuestionAttemptForMe(authId, question.id, any()) } returns
            graded(correct = false, feedback = "Not the product owner.")

        val result = service.perform(
            BuddyActionRequest(action = "answer_question", questionId = question.id, answer = "The PO"),
            jwt,
        )

        // ok = true: the action was to send their answer, and it was sent. A missed question shown
        // as a failed action reads as the buddy breaking rather than as an ordinary retry.
        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Not quite")
        assertThat(result.message).contains("try again")
    }

    @Test
    fun `an answer confirmed after the question was passed on the page is not sent again`() = runTest {
        // A button can outlive the state it was offered against. A second attempt would be kept for
        // nothing, and a wrong one would read as having lost the pass.
        val question = question("Who runs the retro?", QuestionStatus.PASSED, options = listOf("The SM", "The PO"))
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findQuestion(userId, question.id) } returns question

        val result = service.perform(
            BuddyActionRequest(action = "answer_question", questionId = question.id, answer = "The SM"),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("already passed")
        verify(exactly = 0) { questionAttemptService.submitQuestionAttemptForMe(any(), any(), any()) }
    }

    @Test
    fun `a short-text answer is sent as the hire wrote it`() = runTest {
        val question = question("What is your definition of done?", QuestionStatus.RETRY)
            .copy(type = CheckQuestionType.SHORT_TEXT, options = emptyList())
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findQuestion(userId, question.id) } returns question
        val submitted = slot<SubmitQuestionAttemptRequest>()
        every {
            questionAttemptService.submitQuestionAttemptForMe(authId, question.id, capture(submitted))
        } returns graded(correct = true)

        service.perform(
            BuddyActionRequest(
                action = "answer_question",
                questionId = question.id,
                answer = "Reviewed, merged and deployed.",
            ),
            jwt,
        )

        assertThat(submitted.captured.textAnswer).isEqualTo("Reviewed, merged and deployed.")
        assertThat(submitted.captured.selectedOptionIds).isEmpty()
    }

    // -- request_skip -----------------------------------------------------------------------------

    @Test
    fun `a skip request without a reason sends the mentor back to ask why`() {
        // The PM decides on the reason; a request with none is one that sits.
        val step = step("Set up the VPN", StepStatus.WAITING)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("request_skip", "step_id" to step.id.toString()), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("ask why they want to skip it")
    }

    @Test
    fun `a step already waiting on a skip decision cannot get a second request`() {
        val step = step("Set up the VPN", StepStatus.WAITING).copy(skip = skip(accepted = null))
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(
            call("request_skip", "step_id" to step.id.toString(), "reason" to "I already have access."),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("has not decided yet")
        // Where they can change it instead, since a second request is not possible.
        assertThat(outcome.toolResult).contains("/onboarding/${step.id}")
    }

    @Test
    fun `the skip proposal carries the reason, and proposing sends nothing`() {
        val step = step("Set up the VPN", StepStatus.WAITING)
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(
            call("request_skip", "step_id" to step.id.toString(), "reason" to "I already have VPN access."),
            userId,
        )

        assertThat(outcome.proposal?.label).isEqualTo("Ask your PM to skip “Set up the VPN”")
        assertThat(outcome.proposal?.reason).isEqualTo("I already have VPN access.")
        assertThat(outcome.toolResult).contains("Do not promise it will be accepted")
        verify(exactly = 0) { onboardingSkipService.createOnboardingSkipForMe(any(), any(), any()) }
    }

    @Test
    fun `asking again after a decline puts the PM's comment in front of the mentor`() {
        val step = step("Set up the VPN", StepStatus.WAITING)
            .copy(skip = skip(accepted = false, reviewComment = "Everyone needs the company VPN."))
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(
            call("request_skip", "step_id" to step.id.toString(), "reason" to "I work on-site only."),
            userId,
        )

        assertThat(outcome.proposal).isNotNull()
        assertThat(outcome.toolResult).contains("“Everyone needs the company VPN.”")
        assertThat(outcome.toolResult).contains("addresses that")
    }

    @Test
    fun `a confirmed skip request goes through the hire's own skip route`() = runTest {
        val step = step("Set up the VPN", StepStatus.WAITING)
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findStep(userId, step.id) } returns step
        val sent = slot<CreateOnboardingSkipRequest>()
        every { onboardingSkipService.createOnboardingSkipForMe(authId, step.id, capture(sent)) } returns
            CreateOnboardingSkipResponse(
                id = UUID.randomUUID(),
                stepId = step.id,
                status = SkipStatus.PENDING,
                reason = "I already have VPN access.",
                createdAt = Instant.EPOCH,
            )

        val result = service.perform(
            BuddyActionRequest(action = "request_skip", stepId = step.id, reason = "I already have VPN access."),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(sent.captured.reason).isEqualTo("I already have VPN access.")
        assertThat(result.message).contains("your PM will decide")
    }

    @Test
    fun `completing a step with a pending skip says on the button that it withdraws the request`() {
        // The completion route drops a pending skip. Allowed, but never silently.
        val step = step("Set up the VPN", StepStatus.IN_PROGRESS).copy(skip = skip(accepted = null))
        every { buddyPathTools.findStep(userId, step.id) } returns step

        val outcome = service.propose(call("complete_step", "step_id" to step.id.toString()), userId)

        assertThat(outcome.proposal?.label).contains("withdraws your skip request")
        assertThat(outcome.toolResult).contains("finishing it withdraws that request")
    }

    // -- add_path_step ----------------------------------------------------------------------------

    @Test
    fun `a step with no description is refused, because a title alone is a guess`() {
        val phase = phase("Deployment")
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            call("add_path_step", "phase_id" to phase.id.toString(), "title" to "Learn the deploy"),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("has to guess at")
    }

    @Test
    fun `a step already on the path is not added a second time`() {
        val phase = phase("Setup", steps = listOf(step("Clone the repository", StepStatus.WAITING)))
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            call(
                "add_path_step",
                "phase_id" to phase.id.toString(),
                "title" to "clone the REPOSITORY",
                "description" to "Get the code onto your machine.",
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("already a step")
    }

    @Test
    fun `the proposal carries the phase, the title and the description`() {
        val phase = phase("Deployment")
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase

        val outcome = service.propose(
            call(
                "add_path_step",
                "phase_id" to phase.id.toString(),
                "title" to "Walk through a release",
                "description" to "Sit with whoever cuts the next release.",
            ),
            userId,
        )

        assertThat(outcome.proposal?.label).isEqualTo("Add “Walk through a release” to your path")
        assertThat(outcome.proposal?.phaseId).isEqualTo(phase.id)
        assertThat(outcome.proposal?.description).isEqualTo("Sit with whoever cuts the next release.")
        // The line that makes this safe to offer at all, stated where the model reads it.
        assertThat(outcome.toolResult).contains("their PM's blueprint is untouched")
    }

    @Test
    fun `a confirmed step lands at the end of the phase, as a task`() = runTest {
        val phase = phase("Deployment", steps = listOf(step("Read the runbook", StepStatus.FINISHED)))
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { buddyPathTools.findPhase(userId, phase.id) } returns phase
        val created = slot<CreateOnboardingStepRequest>()
        every {
            onboardingStepService.createOnboardingStepForMe(
                authId,
                phase.id,
                capture(created),
                // The origin, asserted by being the only stub that matches: a step the buddy added
                // used to arrive labelled "Custom step by PM" -- something the hire's team requires
                // -- when it was something they agreed to in a chat.
                StepOrigin.BUDDY,
            )
        } returns createdStep("Walk through a release")

        val result = service.perform(
            BuddyActionRequest(
                action = "add_path_step",
                phaseId = phase.id,
                title = "Walk through a release",
                description = "Sit with whoever cuts the next release.",
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        // At the end: where a step the conversation produced belongs in somebody else's sequence is
        // not something this can know, and the hire can drag it.
        assertThat(created.captured.position).isEqualTo(1)
        assertThat(created.captured.type).isEqualTo(StepType.TASK)
        // No expected outcome: that is a promise about what the step leaves somebody able to do,
        // and the mentor is not in a position to make one.
        assertThat(created.captured.expectedOutcome).isEmpty()
        assertThat(result.message).contains("it is yours")
    }

    // -- fixtures ---------------------------------------------------------------------------------

    private fun call(name: String, vararg args: Pair<String, String>) = BuddyToolCallDto(
        id = "c0",
        name = name,
        arguments = buildJsonObject { args.forEach { (k, v) -> put(k, v) } },
    )

    private fun phase(title: String, steps: List<GetOnboardingStepsResponse> = emptyList()) =
        GetOnboardingPhaseForUserResponse(
            id = UUID.randomUUID(),
            pathId = UUID.randomUUID(),
            position = 0,
            title = title,
            description = "",
            locked = false,
            steps = steps,
        )

    private fun step(title: String, status: StepStatus, locked: Boolean = false) =
        GetOnboardingStepsResponse(
            id = UUID.randomUUID(),
            phaseId = UUID.randomUUID(),
            position = 0,
            title = title,
            description = "",
            type = StepType.TASK,
            estimatedMinutes = 20,
            isAiAssisted = false,
            status = status,
            completedAt = null,
            skip = null,
            locked = locked,
        )

    private fun question(
        text: String,
        status: QuestionStatus,
        options: List<String> = emptyList(),
    ) = GetOnboardingQuestionForUserResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = 0,
        type = CheckQuestionType.MULTIPLE_CHOICE,
        question = text,
        options = options.mapIndexed { index, label ->
            QuestionOptionForUserResponse(id = UUID.randomUUID(), position = index, label = label)
        },
        status = status,
    )

    private fun skip(accepted: Boolean?, reviewComment: String? = null) = GetOnboardingStepSkipResponse(
        id = UUID.randomUUID(),
        stepId = UUID.randomUUID(),
        reason = "I already know this.",
        accepted = accepted,
        reviewComment = reviewComment,
        reviewedAt = null,
    )

    private fun task(title: String, finished: Boolean, stepId: UUID) = GetOnboardingTaskResponse(
        id = UUID.randomUUID(),
        stepId = stepId,
        position = 2,
        title = title,
        description = "what it involves",
        finished = finished,
    )

    private fun updatedTask(title: String) = UpdateOnboardingTaskResponse(
        id = UUID.randomUUID(),
        stepId = UUID.randomUUID(),
        position = 2,
        title = title,
        description = "what it involves",
        finished = true,
    )

    private fun completed(title: String) = UpdateOnboardingStepResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = 0,
        title = title,
        description = "",
        estimatedMinutes = 20,
        isAiAssisted = false,
        expectedOutcome = "",
        status = StepStatus.FINISHED,
        completedAt = Instant.EPOCH,
        skip = null,
    )

    private fun createdStep(title: String) = CreateOnboardingStepResponse(
        id = UUID.randomUUID(),
        phaseId = UUID.randomUUID(),
        position = 1,
        title = title,
        description = "",
        type = StepType.TASK,
        estimatedMinutes = 15,
        isAiAssisted = false,
        expectedOutcome = "",
        status = StepStatus.WAITING,
    )

    private fun graded(
        correct: Boolean,
        explanation: String? = null,
        feedback: String? = null,
    ) = SubmitQuestionAttemptResponse(
        attemptId = UUID.randomUUID(),
        questionId = UUID.randomUUID(),
        correct = correct,
        createdAt = Instant.EPOCH,
        explanation = explanation,
        feedback = feedback,
        status = if (correct) QuestionStatus.PASSED else QuestionStatus.RETRY,
    )
}
