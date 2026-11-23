package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.buddy.external.enums.BuddyActionType
import com.sprintstart.sprintstartbackend.buddy.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.path.external.HirePathActionsApi
import com.sprintstart.sprintstartbackend.path.external.HirePathApi
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
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
class BuddyPathActions(
    private val buddyPathTools: BuddyPathTools,
    private val hirePathActionsApi: HirePathActionsApi,
    private val edits: BuddyPathEditActions,
    private val hirePathApi: HirePathApi,
    private val questions: BuddyQuestionActions,
    private val skips: BuddySkipActions,
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
            listOf(
                COMPLETE_STEP_SPEC,
                COMPLETE_TASK_SPEC,
                BuddyQuestionActions.SPEC,
                BuddyPathEditActions.SPEC,
                BuddySkipActions.SPEC,
            )
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
            BuddyActionType.ANSWER_QUESTION -> questions.propose(call, type, UserId(userId))
            BuddyActionType.REQUEST_SKIP -> skips.propose(call, type, UserId(userId))
            BuddyActionType.ADD_PATH_STEP -> edits.propose(call, type, UserId(userId))
            else -> error("${type.toolName} is not a path action")
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
            BuddyActionType.ANSWER_QUESTION -> questions.perform(authId, UserId(resolveUserId(authId)), request)
            BuddyActionType.REQUEST_SKIP -> skips.perform(authId, UserId(resolveUserId(authId)), request)
            BuddyActionType.ADD_PATH_STEP -> edits.perform(authId, UserId(resolveUserId(authId)), request)
            else -> error("${type.toolName} is not a path action")
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
        val stepId = call.uuidArgument("step_id")
            ?: return notProposed(
                "No step_id was provided. Read get_my_onboarding_path and pass the step_id of the " +
                    "step they mean.",
            )
        val step = buddyPathTools.findStep(userId, stepId)
            ?: return notProposed(
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
        if (refusal != null) return notProposed(refusal)

        // Finishing a step withdraws a skip request still waiting on the PM. Allowed -- somebody who
        // did the step anyway should be able to close it -- but never without saying so, on the
        // button and to the mentor, because nothing else would tell them the request is gone.
        val pendingSkip = step.hasPendingSkip()
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
        val taskId = call.uuidArgument("task_id")
            ?: return notProposed(
                "No task_id was provided. Read get_my_onboarding_path for the checklist of the step " +
                    "they are on and pass the task_id of the line they mean.",
            )
        val task = findTask(userId, taskId)
            ?: return notProposed(
                "No checklist line of this hire's own path has that id. Read get_my_onboarding_path " +
                    "again — it only carries the checklist of the step they are on, and a line of some " +
                    "other step is not in front of you.",
            )
        if (task.finished) {
            return notProposed(
                "“${task.title}” is already ticked off. Tell them it is already done rather than " +
                    "offering it again.",
            )
        }
        // The task route does not check locks; the page does, by never opening a locked step.
        if (buddyPathTools.findStep(userId, task.stepId)?.locked == true) {
            return notProposed(
                "The step that “${task.title}” belongs to is locked, so nothing on its checklist can be " +
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
        val task = runCatching { hirePathApi.task(OnboardingTaskId(taskId)) }.getOrNull()
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
        val task = hirePathApi.myTask(AuthId(authId), OnboardingTaskId(taskId))
        if (buddyPathTools.findStep(resolveUserId(authId), task.stepId)?.locked == true) {
            return BuddyActionResponse(
                ok = false,
                message = "That step is locked right now, so its checklist can't be ticked yet.",
            )
        }
        hirePathActionsApi.updateMyTask(
            AuthId(authId),
            OnboardingTaskId(taskId),
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
        val step = hirePathActionsApi.completeMyStep(AuthId(authId), StepId(stepId))
        return BuddyActionResponse(
            ok = true,
            message = "Ticked off “${step.title}”. It is done on your path — if that was too early, " +
                "you can reopen it there.",
        )
    }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    private companion object {
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
    }
}
