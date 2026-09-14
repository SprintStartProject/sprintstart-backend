package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyActionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.QuestionOptionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The actions that let a conversation move the hire along their onboarding path.
 *
 * A component of its own rather than more branches in [BuddyActionService], for the reason
 * [BuddyBoardTools] is one: they share a subject that the rest of the catalog does not touch, and
 * they come as a set. [BuddyActionService] still owns the propose/confirm contract — it routes to
 * this and emits what comes back — so there is exactly one place where an action becomes a button.
 *
 * ### The line these do not cross
 *
 * They write to **the hire's own copy of the path**, never to the blueprint it was copied from. The
 * curriculum is the PM's: a mentor that could edit it is a mentor whose team stops trusting it.
 * Everything here is undoable on the hire's own onboarding page, which is what makes proposing any
 * of it reasonable.
 *
 * ### Why they sit outside the project gate
 *
 * An onboarding path belongs to a *person*. It is generated from one project's blueprint, but the
 * path itself is not project-scoped — so gating these would refuse a hire onboarding on two
 * projects, who still has exactly one path, on the page they are looking at.
 */
@Component
// One propose and one perform per action, plus the argument readers. The count tracks how many
// actions there are.
@Suppress("TooManyFunctions")
class BuddyPathActions(
    private val buddyPathTools: BuddyPathTools,
    private val onboardingStepService: OnboardingStepService,
    private val onboardingStepPlacementService: OnboardingStepPlacementService,
    private val onboardingTaskService: OnboardingTaskService,
    private val questionAttemptService: QuestionAttemptService,
    private val onboardingSkipService: OnboardingSkipService,
    private val userApi: UserApi,
) {
    /**
     * The path actions, or none at all for a hire without a path.
     *
     * Gated on the path's existence and on nothing else — never on how far along it they are. A
     * mentor that may only propose something once some further condition holds is one whose refusals
     * the hire has to learn; each proposal checks its own preconditions, where the reason can be a
     * sentence. Asked of [BuddyPathTools], so the read tool and these actions can never disagree
     * about whether there is a path.
     */
    fun specs(userId: UUID): List<BuddyToolSpecDto> =
        if (!buddyPathTools.hasPath(userId)) {
            emptyList()
        } else {
            listOf(COMPLETE_STEP_SPEC, COMPLETE_TASK_SPEC, ANSWER_QUESTION_SPEC, ADD_PATH_STEP_SPEC, REQUEST_SKIP_SPEC)
        }

    /** Whether [type] is one of this component's actions. */
    fun handles(type: BuddyActionType): Boolean =
        type == BuddyActionType.COMPLETE_STEP ||
            type == BuddyActionType.COMPLETE_TASK ||
            type == BuddyActionType.ANSWER_QUESTION ||
            type == BuddyActionType.ADD_PATH_STEP ||
            type == BuddyActionType.REQUEST_SKIP

    /**
     * Offers one of them, checked against the hire's own path before the hire sees a button.
     *
     * Every precondition is resolved here rather than at confirm time, for the reason the assessment
     * proposal gives: a step that is already finished, a locked question, an answer that matches no
     * option — discovered now, each is a correction the mentor can act on mid-sentence; discovered
     * after the click, each is a hire pressing a button and being told no.
     *
     * The refusals are addressed to the mentor, not to the hire, and each says what to do instead. A
     * tool result that only says "no" is one the model relays as a broken feature.
     */
    fun propose(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        userId: UUID,
    ): BuddyActionService.ProposeOutcome =
        when (type) {
            BuddyActionType.COMPLETE_STEP -> proposeCompleteStep(call, type, userId)
            BuddyActionType.COMPLETE_TASK -> proposeCompleteTask(call, type, userId)
            BuddyActionType.ANSWER_QUESTION -> proposeAnswer(call, type, userId)
            BuddyActionType.REQUEST_SKIP -> proposeSkip(call, type, userId)
            else -> proposeAddPathStep(call, type, userId)
        }

    /** Runs a confirmed path action. Each underlying `/me/...` operation owns its own rules. */
    fun perform(
        type: BuddyActionType,
        authId: String,
        request: BuddyActionRequest,
    ): BuddyActionResponse =
        when (type) {
            BuddyActionType.COMPLETE_STEP -> completeStep(authId, request.stepId)
            BuddyActionType.COMPLETE_TASK -> completeTask(authId, request.onboardingTaskId)
            BuddyActionType.ANSWER_QUESTION -> answerQuestion(authId, request.questionId, request.answer)
            BuddyActionType.REQUEST_SKIP -> requestSkip(authId, request.stepId, request.reason)
            else -> addPathStep(authId, request)
        }

    /**
     * Offers to tick a step of the hire's path off.
     *
     * The button names the step, and the tool result tells the mentor to *ask* rather than to
     * announce: the one thing this must never become is a mentor that marks work done because the
     * conversation went well. Only the hire knows whether they did it.
     */
    private fun proposeCompleteStep(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        userId: UUID,
    ): BuddyActionService.ProposeOutcome {
        val stepId = call.uuidArg("step_id")
            ?: return refused(
                "No step_id was provided. Read get_my_onboarding_path and pass the step_id of the " +
                    "step they mean.",
            )
        val step = buddyPathTools.findStep(userId, stepId)
            ?: return refused(
                "No step of this hire's own path has that id. Read get_my_onboarding_path again — an " +
                    "id from anywhere else is not theirs to tick off.",
            )

        val refusal = when {
            step.status == StepStatus.FINISHED ->
                "“${step.title}” is already done, so there is nothing to tick. Tell them it is " +
                    "already behind them."
            step.status == StepStatus.SKIPPED ->
                "“${step.title}” was skipped, so it cannot be completed. If that was wrong, their PM " +
                    "is the one who can undo it."
            step.locked ->
                "“${step.title}” is locked: something it waits on is not finished. Say what it is " +
                    "waiting on rather than offering to tick it."
            else -> null
        }
        if (refusal != null) return refused(refusal)

        // Finishing a step withdraws a skip request still waiting on the PM. Allowed -- somebody who
        // did the step anyway should be able to close it -- but never without saying so, on the
        // button and to the mentor, because nothing else would tell them the request is gone.
        val pendingSkip = step.skip != null && step.skip.accepted == null
        val withdraws = if (pendingSkip) {
            " They asked their PM to skip this step and nobody has decided yet: finishing it withdraws " +
                "that request, so say so plainly before they click."
        } else {
            ""
        }

        return BuddyActionService.ProposeOutcome(
            toolResult = "Proposed to the hire: mark “${step.title}” as done. They see a confirm " +
                "button and nothing changes unless they click it. Ask whether they have actually " +
                "done it — never say that it is done.$withdraws",
            proposal = BuddyActionService.BuddyActionProposal(
                action = type.toolName,
                label = if (pendingSkip) {
                    "Mark “${step.title}” as done (withdraws your skip request)"
                } else {
                    "Mark “${step.title}” as done"
                },
                question = null,
                stepId = step.id,
            ),
        )
    }

    /**
     * Offers to send the hire's own answer to a knowledge question.
     *
     * ### Why the answer is text and not an option id
     *
     * The mentor is not told which option is correct (see [BuddyPathTools]), so what it passes here
     * is what the hire said — "the second one", "the retro", the words themselves — and the match to
     * an option happens server-side in [matchOption], once here and once at confirm time, from the
     * same input. That is deliberate: an option id in the proposal would be the mentor choosing, and
     * the point is that the hire chooses.
     *
     * A match that cannot be made comes back naming the options, so the mentor asks again instead of
     * guessing on the hire's behalf.
     */
    private fun proposeAnswer(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        userId: UUID,
    ): BuddyActionService.ProposeOutcome {
        val questionId = call.uuidArg("question_id")
            ?: return refused(
                "No question_id was provided. Read get_my_onboarding_path and pass the question_id " +
                    "of the question they are answering.",
            )
        val question = buddyPathTools.findQuestion(userId, questionId)
            ?: return refused(
                "No question of this hire's own path has that id. Read get_my_onboarding_path again.",
            )
        val answer = call.stringArg("answer").trim()

        val refusal = when {
            answer.isBlank() ->
                "No answer was provided. This has to be the hire's own answer in their own words — " +
                    "ask them what they want to send, and never answer it for them."
            question.status == QuestionStatus.PASSED ->
                "They have already passed “${question.question}”, so there is nothing to send. Tell " +
                    "them it is behind them."
            question.status == QuestionStatus.LOCKED ->
                "“${question.question}” is locked: something it waits on is not finished yet. Say " +
                    "what it is waiting on rather than offering to answer it."
            else -> null
        }
        if (refusal != null) return refused(refusal)

        val shown = if (question.type == CheckQuestionType.MULTIPLE_CHOICE) {
            matchOption(question, answer)?.label
                ?: return refused(
                    "“$answer” does not match exactly one of the options for that question. The " +
                        "options are: ${question.options.joinToString("; ") { it.label }}. Ask the " +
                        "hire which of them they mean and pass that back.",
                )
        } else {
            answer
        }

        return BuddyActionService.ProposeOutcome(
            toolResult = "Proposed to the hire: send “$shown” as their answer to " +
                "“${question.question}”. They see a confirm button showing that answer, and nothing " +
                "is recorded unless they click it. An attempt is kept whether it is right or not, so " +
                "make sure it is the answer they meant — and do not tell them whether it is correct, " +
                "because you have not been told either.",
            proposal = BuddyActionService.BuddyActionProposal(
                action = type.toolName,
                label = "Send this answer: “${shown.take(ANSWER_LABEL_LIMIT)}”",
                question = null,
                questionId = question.id,
                answer = answer,
            ),
        )
    }

    /**
     * Offers to send the hire's skip request for one step to their PM.
     *
     * The same request the step page files, reached from a conversation: the mentor's part is
     * helping the hire say *why*, because a reason the PM can act on is the difference between a
     * request that is decided and one that sits. The reason goes out in the hire's name, so the
     * proposal carries all of it and the button shows it before anything is sent.
     *
     * Checked here rather than left to the route, so the mentor hears "already asked" or "already
     * done" while it can still say something useful about it.
     */
    private fun proposeSkip(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        userId: UUID,
    ): BuddyActionService.ProposeOutcome {
        val stepId = call.uuidArg("step_id")
            ?: return refused(
                "No step_id was provided. Read get_my_onboarding_path and pass the step_id of the " +
                    "step they want to skip.",
            )
        val step = buddyPathTools.findStep(userId, stepId)
            ?: return refused(
                "No step of this hire's own path has that id. Read get_my_onboarding_path again.",
            )
        val reason = call.stringArg("reason").trim()
        val previous = step.skip

        val refusal = when {
            step.status == StepStatus.FINISHED ->
                "“${step.title}” is already done, so there is nothing to skip."
            step.status == StepStatus.SKIPPED ->
                "“${step.title}” is already skipped — their PM accepted it."
            previous != null && previous.accepted == null ->
                "They already asked to skip “${step.title}” and their PM has not decided yet. A second " +
                    "request cannot be sent; they can change the reason on the step's own page " +
                    "(${BuddyPathTools.STEP_PAGE_LINK}${step.id})."
            reason.isBlank() ->
                "No reason was provided. Their PM decides on the reason, so ask why they want to skip " +
                    "it — already know it, not relevant to their role, covered elsewhere — and put " +
                    "that into a sentence or two before offering this again."
            else -> null
        }
        if (refusal != null) return refused(refusal)

        // A declined request can be asked again, and the PM said why the first time. The new reason
        // should answer that, or it will be declined for the same thing.
        val declinedBefore = previous
            ?.takeIf { it.accepted == false }
            ?.let { declined ->
                " Their PM declined an earlier request" +
                    (declined.reviewComment?.takeIf { it.isNotBlank() }?.let { " with: “$it”" } ?: "") +
                    " — make sure this reason addresses that."
            }.orEmpty()

        return BuddyActionService.ProposeOutcome(
            toolResult = "Proposed to the hire: ask their PM to skip “${step.title}”, with the reason " +
                "“$reason”. They see a confirm button showing that reason, and nothing is sent unless " +
                "they click it. Their PM decides; until then the step stays on their path, and if it " +
                "is accepted it counts as done and unlocks what waits on it. Do not promise it will " +
                "be accepted.$declinedBefore",
            proposal = BuddyActionService.BuddyActionProposal(
                action = type.toolName,
                label = "Ask your PM to skip “${step.title}”",
                question = null,
                stepId = step.id,
                reason = reason,
            ),
        )
    }

    /** Files the confirmed skip request through the hire's own route, which owns every rule about it. */
    private fun requestSkip(authId: String, stepId: UUID?, reason: String?): BuddyActionResponse {
        if (stepId == null || reason.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No skip request was proposed to send.")
        }
        val step = buddyPathTools.findStep(resolveUserId(authId), stepId)
            ?: return BuddyActionResponse(ok = false, message = "That step isn't on your path.")

        onboardingSkipService.createOnboardingSkipForMe(authId, stepId, CreateOnboardingSkipRequest(reason = reason))
        return BuddyActionResponse(
            ok = true,
            message = "Sent — your PM will decide on skipping “${step.title}”. Until then it stays on " +
                "your path; you can change or withdraw the reason on the step's page.",
        )
    }

    /**
     * Offers to add a step the conversation produced to a phase of the hire's own path.
     *
     * The one action here that writes something the model wrote, so the line is worth restating: it
     * adds to the hire's copy, their PM's blueprint is untouched, and the hire can edit or delete it
     * on their own page like any other step.
     *
     * A description is required rather than optional. A title on its own is a step somebody has to
     * guess at, and the phase it lands in is usually one that came back empty — precisely the case
     * where the hire has nothing else to go on.
     */
    private fun proposeAddPathStep(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        userId: UUID,
    ): BuddyActionService.ProposeOutcome {
        val phaseId = call.uuidArg("phase_id")
            ?: return refused(
                "No phase_id was provided. Read get_my_onboarding_path and pass the phase_id of the " +
                    "phase the step belongs in.",
            )
        val phase = buddyPathTools.findPhase(userId, phaseId)
            ?: return refused(
                "No phase of this hire's own path has that id. Read get_my_onboarding_path again.",
            )
        val title = call.stringArg("title").trim()
        val description = call.stringArg("description").trim()
        val placement = PathStepPlacement.inferred(phase, call.uuidListArg("waits_on"), call.uuidListArg("unlocks"))

        val refusal = when {
            title.isBlank() -> "No title was provided. Say what the step is, in a few words."
            description.isBlank() ->
                "No description was provided. A step with a title and nothing else is one the hire " +
                    "has to guess at — say what doing it involves, then offer it again."
            phase.steps.any { it.title.trim().equals(title, ignoreCase = true) } ->
                "“$title” is already a step of “${phase.title}”, so nothing needs adding. Point them " +
                    "at the one that is there."
            else -> reopensFinishedPhase(userId, phase) ?: placement.problem()
        }
        if (refusal != null) return refused(refusal)

        val where = placement.describe()
        val connection = if (placement.isConnected) {
            " It goes $where in the phase's graph: it opens once what it waits on is done, and what " +
                "it unlocks now waits on it."
        } else {
            " It is not connected to anything, so it is open straight away and is never what comes " +
                "next — if it belongs somewhere in their path, pass waits_on and unlocks."
        }
        val entry = if (placement.entryInferred) {
            " You passed no waits_on, so it opens after what the path says it should (the button " +
                "names it) -- if that is not where they meant, offer it again with waits_on."
        } else {
            ""
        }
        val relocks = placement
            .relocksStarted()
            .takeIf { it.isNotEmpty() }
            ?.let {
                " ${it.joinToString(", ")} is already started and will lock again until this step is " +
                    "done — say so before they click."
            }.orEmpty()

        return BuddyActionService.ProposeOutcome(
            toolResult = "Proposed to the hire: add the step “$title” to the phase " +
                "“${phase.title}” of their own path. They see a confirm button; nothing is added " +
                "unless they click it. This changes their copy only — their PM's blueprint is " +
                "untouched — and they can edit or remove it afterwards. Say what the step is for " +
                "and where it goes before you offer it.$connection$entry$relocks",
            proposal = BuddyActionService.BuddyActionProposal(
                action = type.toolName,
                label = if (where.isEmpty()) "Add “$title” to your path" else "Add “$title” $where",
                question = null,
                phaseId = phase.id,
                title = title,
                description = description,
                waitsOnIds = placement.waitsOn.toList(),
                unlocksIds = placement.unlocks.toList(),
            ),
        )
    }

    /**
     * Why adding a step to [phase] would take away something the hire already has, or null.
     *
     * A finished phase is what unlocked every phase that waits on it. A new open step makes it
     * unfinished again, and those phases lock -- a hire asking for one extra step would find the phase
     * they were working in shut. The page's own "add step" has the same effect, which is exactly why
     * the mentor should not reach for it without knowing.
     */
    private fun reopensFinishedPhase(userId: UUID, phase: GetOnboardingPhaseForUserResponse): String? {
        val finished = (phase.steps.isNotEmpty() || phase.questions.isNotEmpty()) &&
            phase.steps.all { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED } &&
            phase.questions.all { it.status == QuestionStatus.PASSED }
        if (!finished) return null
        val waiting = buddyPathTools.phasesOf(userId).filter { phase.id in it.blockerIds }
        if (waiting.isEmpty()) return null
        return "“${phase.title}” is finished, and ${waiting.joinToString(", ") { "“${it.title}”" }} " +
            "waits on it: a new step there would lock that again until it is done. Put the step in the " +
            "phase they are standing in instead."
    }

    /**
     * Offers to tick one line off the checklist of a step.
     *
     * The finer of the two claims, and the reason both exist. A hire who says "I have done the first
     * two" has not finished the step, and a mentor holding only `complete_step` would either overstate
     * that or drop it. The read tool carries the checklist of the step they are on, which is where the
     * task_id comes from.
     */
    private fun proposeCompleteTask(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        userId: UUID,
    ): BuddyActionService.ProposeOutcome {
        val taskId = call.uuidArg("task_id")
            ?: return refused(
                "No task_id was provided. Read get_my_onboarding_path for the checklist of the step " +
                    "they are on and pass the task_id of the line they mean.",
            )
        val task = findTask(userId, taskId)
            ?: return refused(
                "No checklist line of this hire's own path has that id. Read get_my_onboarding_path " +
                    "again — it only carries the checklist of the step they are on, and a line of some " +
                    "other step is not in front of you.",
            )
        if (task.finished) {
            return refused(
                "“${task.title}” is already ticked off. Tell them it is already done rather than " +
                    "offering it again.",
            )
        }
        // The task route does not check locks; the page does, by never opening a locked step.
        if (buddyPathTools.findStep(userId, task.stepId)?.locked == true) {
            return refused(
                "The step “${task.title}” belongs to is locked, so nothing on its checklist can be " +
                    "ticked yet. Say what the step is waiting on instead.",
            )
        }

        return BuddyActionService.ProposeOutcome(
            toolResult = "Proposed to the hire: tick “${task.title}” off their checklist. They see a " +
                "confirm button and nothing changes unless they click it. Ask whether they have done " +
                "it; ticking a line because the conversation covered it is not the same thing.",
            proposal = BuddyActionService.BuddyActionProposal(
                action = type.toolName,
                label = "Tick off “${task.title}”",
                question = null,
                onboardingTaskId = task.id,
            ),
        )
    }

    /**
     * One checklist line of the hire's own path, or null.
     *
     * Ownership is the *step's*: a line belongs to this hire exactly when the step it hangs on is on
     * their path, which [BuddyPathTools.findStep] answers. That is why the unscoped read by id is safe
     * here and would not be on its own.
     */
    private fun findTask(userId: UUID, taskId: UUID): GetOnboardingTaskResponse? {
        val task = runCatching { onboardingTaskService.getOnboardingTaskById(taskId) }.getOrNull()
            ?: return null
        return task.takeIf { buddyPathTools.findStep(userId, it.stepId) != null }
    }

    /**
     * Ticks the confirmed line off.
     *
     * Read first and written back whole, because the endpoint that owns this takes the task as it
     * should now be rather than a patch. Everything else on it is echoed back unchanged: the mentor is
     * changing one tick box, not editing the hire's checklist.
     */
    private fun completeTask(authId: String, taskId: UUID?): BuddyActionResponse {
        if (taskId == null) {
            return BuddyActionResponse(ok = false, message = "No checklist line was proposed to tick off.")
        }
        val task = onboardingTaskService.getOnboardingTaskForMe(authId, taskId)
        if (buddyPathTools.findStep(resolveUserId(authId), task.stepId)?.locked == true) {
            return BuddyActionResponse(
                ok = false,
                message = "That step is locked right now, so its checklist can't be ticked yet.",
            )
        }
        onboardingTaskService.updateOnboardingTaskForMe(
            authId,
            taskId,
            UpdateOnboardingTaskRequest(
                position = task.position,
                title = task.title,
                description = task.description,
                finished = true,
            ),
        )
        return BuddyActionResponse(
            ok = true,
            message = "Ticked “${task.title}” off. The step itself is still yours to finish when you " +
                "are ready — a checklist does not close it.",
        )
    }

    /**
     * Ticks the confirmed step off.
     *
     * The lock is checked again here, because the route underneath does not check it — the hire's
     * page enforces it by never offering the button — and a proposal can sit in a conversation long
     * enough for the path around it to change. Finished and skipped stay with the route, which
     * refuses both with a sentence of its own.
     */
    private fun completeStep(authId: String, stepId: UUID?): BuddyActionResponse {
        if (stepId == null) {
            return BuddyActionResponse(ok = false, message = "No step was proposed to complete.")
        }
        val current = buddyPathTools.findStep(resolveUserId(authId), stepId)
            ?: return BuddyActionResponse(ok = false, message = "That step isn't on your path.")
        if (current.locked) {
            return BuddyActionResponse(
                ok = false,
                message = "“${current.title}” is locked right now — something it waits on isn't finished yet.",
            )
        }
        val step = onboardingStepService.completeOnboardingStepForMe(authId, stepId)
        return BuddyActionResponse(
            ok = true,
            message = "Ticked off “${step.title}”. It is done on your path — if that was too early, " +
                "you can reopen it there.",
        )
    }

    /**
     * Records the confirmed answer, and relays what came back.
     *
     * `ok` is true for a wrong answer too, and that is not an oversight: the action was to *send*
     * their answer, and it was sent. Whether it was right is the message's job — a wrong attempt
     * shown as a failed action would read as the buddy having broken rather than as the hire having
     * missed, on the one surface where missing is supposed to be ordinary.
     */
    private fun answerQuestion(authId: String, questionId: UUID?, answer: String?): BuddyActionResponse {
        if (questionId == null || answer.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No answer was proposed to send.")
        }
        val question = buddyPathTools.findQuestion(resolveUserId(authId), questionId)
            ?: return BuddyActionResponse(ok = false, message = "That question isn't on your path.")

        // Checked again at confirm time, for the same reason [completeStep] checks the lock: the
        // attempt route grades whatever it is sent, and a button left in the conversation can be
        // clicked after the hire already passed the question on their page. A second attempt there
        // would be kept for no reason, and a wrong one would read as having lost the pass.
        when (question.status) {
            QuestionStatus.PASSED ->
                return BuddyActionResponse(ok = false, message = "You've already passed that one — nothing was sent.")
            QuestionStatus.LOCKED ->
                return BuddyActionResponse(
                    ok = false,
                    message = "That question is locked right now — something it waits on isn't finished yet.",
                )
            else -> Unit
        }

        val submission = if (question.type == CheckQuestionType.MULTIPLE_CHOICE) {
            val option = matchOption(question, answer)
                ?: return BuddyActionResponse(
                    ok = false,
                    message = "I couldn't tell which option “$answer” meant, so nothing was sent.",
                )
            SubmitQuestionAttemptRequest(selectedOptionIds = listOf(option.id))
        } else {
            SubmitQuestionAttemptRequest(textAnswer = answer)
        }

        val result = questionAttemptService.submitQuestionAttemptForMe(authId, questionId, submission)
        // The submit response is the one user-facing place correct answers are revealed, and the
        // hire's own page shows them there. Relaying the same thing keeps the two surfaces telling
        // one story; withholding it here would make the conversation the worse place to answer.
        val learning = listOfNotNull(result.feedback, result.explanation).joinToString(" ")
        return BuddyActionResponse(
            ok = true,
            message = if (result.correct) {
                "That was right — the question is passed. $learning".trim()
            } else {
                "Not quite. It stays open, so you can try again whenever you like. $learning".trim()
            },
        )
    }

    private fun addPathStep(authId: String, request: BuddyActionRequest): BuddyActionResponse {
        val phaseId = request.phaseId
        val title = request.title
        if (phaseId == null || title.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No step was proposed to add.")
        }
        val phase = buddyPathTools.findPhase(resolveUserId(authId), phaseId)
            ?: return BuddyActionResponse(ok = false, message = "That phase isn't on your path.")

        // Checked again: the path can change between the button and the click, and a placement
        // that was sound then can be a loop or point at something finished now.
        val placement = PathStepPlacement(phase, request.waitsOnIds.toSet(), request.unlocksIds.toSet())
        if (placement.problem() != null) {
            return BuddyActionResponse(
                ok = false,
                message = "Your path changed since this was suggested, so the step wasn't added — ask me again.",
            )
        }

        val created = onboardingStepPlacementService.createConnectedStepForMe(
            authId = authId,
            phaseId = phaseId,
            request = CreateOnboardingStepRequest(
                // Next to what it waits on (or in front of what it unlocks), so the list reads in the
                // order the graph opens it. The end, when it is connected to nothing.
                position = placement.position(),
                title = title,
                description = request.description.orEmpty(),
                type = StepType.TASK,
                estimatedMinutes = ADDED_STEP_MINUTES,
                // Left empty on purpose: an expected outcome is a promise about what doing the step
                // will leave somebody able to do, and the mentor is not in a position to make one.
                expectedOutcome = "",
            ),
            // Theirs, but not their idea, and the card says so. Without this the step arrived
            // labelled "Custom step by PM" -- a thing their team requires -- when it was something
            // they agreed to in a chat.
            origin = StepOrigin.BUDDY,
            waitsOn = placement.waitsOn,
            unlocks = placement.unlocks,
        )
        return BuddyActionResponse(
            ok = true,
            message = "Added “${created.title}” to “${phase.title}”. It is on your path now, and it " +
                "is yours — edit it or delete it there like any other step.",
        )
    }

    /**
     * Which option the hire meant, or null when that is not clear enough to act on.
     *
     * Three passes, loosening in a fixed order: the label exactly, then a label containing what they
     * said, then what they said containing a label. Each pass only counts when it matches *one*
     * option — "the first one" against two options that start alike is ambiguous, and guessing there
     * would record an answer the hire did not give.
     *
     * Called from the proposal and from the confirm with the same input, so the option that ends up
     * recorded is the one whose label the hire read on the button.
     */
    private fun matchOption(
        question: GetOnboardingQuestionForUserResponse,
        answer: String,
    ): QuestionOptionForUserResponse? {
        val needle = answer.trim()
        if (needle.isBlank()) return null

        val exact = question.options.filter { it.label.trim().equals(needle, ignoreCase = true) }
        if (exact.size == 1) return exact.single()

        val labelContains = question.options.filter { it.label.contains(needle, ignoreCase = true) }
        if (labelContains.size == 1) return labelContains.single()

        val answerContains = question.options.filter { needle.contains(it.label.trim(), ignoreCase = true) }
        return answerContains.singleOrNull()
    }

    /**
     * A reason and no button.
     *
     * Prefixed, and that prefix is load-bearing. A refusal phrased as ordinary guidance came back
     * from testing as the mentor telling the hire to click a button that was never rendered: from the
     * model's side a tool result is a tool result, and "say what doing it involves and offer it again"
     * reads a lot like "offered". The prefix says the one thing it has to know -- that nothing is on
     * screen -- before the advice it should act on.
     */
    private fun refused(reason: String) = BuddyActionService.ProposeOutcome(NOT_PROPOSED + reason, null)

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    /** Reads a string argument the model passed to a tool, or "" when it is missing/non-text. */
    private fun BuddyToolCallDto.stringArg(name: String): String =
        (arguments[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /**
     * Reads a list of UUIDs the model passed, tolerating a single string where a list was asked for.
     * Anything that is not a UUID is dropped here and caught by the placement check as "not in this
     * phase" only if it parsed -- an unparseable id is simply not a placement.
     */
    private fun BuddyToolCallDto.uuidListArg(name: String): Set<UUID> {
        val raw = when (val value = arguments[name]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(value.contentOrNull)
            else -> emptyList()
        }
        return raw.mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.toSet()
    }

    /** Reads a UUID argument the model passed to a tool, or null when it is missing/unparseable. */
    private fun BuddyToolCallDto.uuidArg(name: String): UUID? =
        runCatching { UUID.fromString(stringArg(name)) }.getOrNull()

    private companion object {
        /** How many characters of an answer the confirm button shows before it is cut. */
        const val ANSWER_LABEL_LIMIT = 60

        /**
         * The estimate a step the buddy added carries.
         *
         * Deliberately a constant the mentor cannot choose. How long a piece of work takes somebody
         * else is not something a model is in a position to say, and a confident "45 min" on a step
         * it invented is worse than a modest default the hire can correct on their own page.
         */
        const val ADDED_STEP_MINUTES = 15

        val COMPLETE_STEP_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.COMPLETE_STEP.toolName,
            description = "Offer to mark one step of the hire's onboarding path as done. Read " +
                "get_my_onboarding_path first and pass that step's step_id. This does NOT complete " +
                "anything by itself: the hire sees a confirm button naming the step, and only they " +
                "can click it. Use it when they say they have finished something, and when " +
                "get_my_onboarding_path lists a step as READY TO CLOSE (its checklist is done but " +
                "the step is still open, so what waits on it stays locked) — never because the " +
                "conversation went well, and never to tidy up their path. Only they know whether " +
                "the work is done, so ask; do not announce it as done.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("step_id") {
                        put("type", "string")
                        put("description", "The step_id from get_my_onboarding_path.")
                    }
                }
                putJsonArray("required") { add("step_id") }
            },
        )

        /**
         * The prefix every refusal here carries.
         *
         * Testing found the mentor telling a hire to click a button that was never rendered: a
         * refusal written as advice ("say what doing it involves and offer it again") reads, from
         * inside the model, a lot like the offer having been made. This says the fact first.
         */
        const val NOT_PROPOSED = "NOT PROPOSED — no button was shown to the hire, so do not tell " +
            "them to confirm anything. "

        val COMPLETE_TASK_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.COMPLETE_TASK.toolName,
            description = "Offer to tick one line off the checklist of the step the hire is on. Read " +
                "get_my_onboarding_path for the checklist and pass that line's task_id. This does " +
                "NOT tick anything by itself; the hire sees a confirm button naming the line. Use it " +
                "when they say they have done part of a step — that is what this is for, and it is " +
                "why it is separate from complete_step: a step can be finished with lines still open, " +
                "and finishing a step is a bigger claim than ticking a line. Ask; do not tick a line " +
                "off because the conversation covered it.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("task_id") {
                        put("type", "string")
                        put("description", "The task_id from the checklist in get_my_onboarding_path.")
                    }
                }
                putJsonArray("required") { add("task_id") }
            },
        )

        val ANSWER_QUESTION_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.ANSWER_QUESTION.toolName,
            description = "Offer to send the hire's answer to one knowledge-check question on their " +
                "path. Read get_my_onboarding_path for the question_id and the options they are " +
                "looking at. Pass THEIR answer, in their words — for a multiple-choice question, " +
                "whichever option they picked; it is matched to an option for you, and an answer " +
                "that matches none comes back so you can ask again. This does NOT record anything " +
                "by itself; they see a confirm button showing the answer that will be sent. An " +
                "attempt is kept whether it is right or wrong. You are a tutor here, not an " +
                "examiner and not a shortcut: explain the material the question is about, from the " +
                "project's own documents, and let them answer it. You are not told which answer is " +
                "correct — so never state one, never hint at one, and never pass an answer they did " +
                "not give. If they ask you to just tell them, say plainly that you do not know it " +
                "and offer to go through the material instead.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("question_id") {
                        put("type", "string")
                        put("description", "The question_id from get_my_onboarding_path.")
                    }
                    putJsonObject("answer") {
                        put("type", "string")
                        put(
                            "description",
                            "The hire's own answer, in their own words. For multiple choice, the " +
                                "option they picked — the label, or enough of it to identify it.",
                        )
                    }
                }
                putJsonArray("required") {
                    add("question_id")
                    add("answer")
                }
            },
        )

        val REQUEST_SKIP_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.REQUEST_SKIP.toolName,
            description = "Offer to send the hire's request to skip one step of their onboarding path " +
                "to their PM. Read get_my_onboarding_path for the step_id. Use it when THEY want to " +
                "skip a step — never suggest skipping to get through the path faster, and never for a " +
                "step just because it looks hard. Their PM decides, so the reason is what matters: " +
                "ask why before you offer this, help them put it into one or two clear sentences " +
                "(what they already know, why it does not apply to their role, where it is covered " +
                "already), and pass that. It goes out in their name, so it must say what they said, " +
                "not what you think. This does NOT send anything by itself; they see a confirm button " +
                "with the reason. Do not promise it will be accepted. A step that already has a " +
                "request waiting cannot get a second one.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("step_id") {
                        put("type", "string")
                        put("description", "The step_id from get_my_onboarding_path.")
                    }
                    putJsonObject("reason") {
                        put("type", "string")
                        put(
                            "description",
                            "Why they want to skip it, in one or two sentences their PM can decide on, " +
                                "written as the hire.",
                        )
                    }
                }
                putJsonArray("required") {
                    add("step_id")
                    add("reason")
                }
            },
        )

        val ADD_PATH_STEP_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.ADD_PATH_STEP.toolName,
            description = "Offer to add one step to a phase of the hire's OWN copy of their " +
                "onboarding path. Read get_my_onboarding_path for the phase_id. This does NOT add " +
                "anything by itself; the hire confirms, and afterwards the step is theirs to edit, " +
                "reorder or delete. It never touches their PM's blueprint — the curriculum is the " +
                "PM's, and you are not editing it. Use it for three things and little else: a phase " +
                "that came back empty, where the two of you have worked out something concrete it " +
                "should contain; something real the hire is stuck on that their path does not " +
                "mention, so it stops living in a conversation that is gone tomorrow; and a " +
                "knowledge question they got wrong, once you have gone through the material, when " +
                "what they missed is bigger than one explanation — then one short refresher step in " +
                "that question's phase, saying what to revisit and where, and never the answer. " +
                "Offer it; do not add one after every wrong answer. Give a title " +
                "of a few words and a description saying what doing it involves. Do not offer a " +
                "step for something already on their path, do not add several at once, and do not " +
                "add one just to have added something.\n" +
                "WHERE IT GOES. A phase is a dependency graph, not a list: an item opens once " +
                "everything it waits on is done. A step added with no connections is open at once, " +
                "floats unconnected in their graph view and is never what comes next. So place it: " +
                "waits_on = the ids it should open after, unlocks = the ids that should wait on it. " +
                "To put it in as the NEXT thing, waits_on is the item they are on or just finished, " +
                "and unlocks is what currently waits on that item (the path read lists it under " +
                "\"opens\"). A refresher for a question they missed goes before that question: " +
                "unlocks = [that question_id]. Leave both empty only for a step that genuinely " +
                "depends on nothing and holds nothing up.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("phase_id") {
                        put("type", "string")
                        put("description", "The phase_id from get_my_onboarding_path.")
                    }
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "What the step is, in a few words.")
                    }
                    putJsonObject("description") {
                        put("type", "string")
                        put(
                            "description",
                            "What doing it involves, in one or two sentences the hire can act on.",
                        )
                    }
                    putJsonObject("waits_on") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put(
                            "description",
                            "step_id/question_id values in the same phase that must be done before " +
                                "this step opens.",
                        )
                    }
                    putJsonObject("unlocks") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put(
                            "description",
                            "step_id/question_id values in the same phase that should wait on this " +
                                "step. They stop waiting directly on anything in waits_on.",
                        )
                    }
                }
                putJsonArray("required") {
                    add("phase_id")
                    add("title")
                    add("description")
                }
            },
        )
    }
}
