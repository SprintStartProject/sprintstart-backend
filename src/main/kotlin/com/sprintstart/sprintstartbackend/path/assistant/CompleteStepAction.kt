package com.sprintstart.sprintstartbackend.path.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireAction
import com.sprintstart.sprintstartbackend.assistant.external.HireActionContext
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.HireActionRequest
import com.sprintstart.sprintstartbackend.assistant.external.HireActionScope
import com.sprintstart.sprintstartbackend.path.external.HirePathActionsApi
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Offers to tick a step of the hire's own path off. */
@Component
@Order(HireActionOrder.COMPLETE_STEP)
class CompleteStepAction(
    private val myPathTool: MyPathTool,
    private val hirePathActionsApi: HirePathActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PERSON
    override val gerund = "tick a step off your path"

    override fun isMountedFor(hire: UserId): Boolean = myPathTool.hasPath(hire.value)

    /**
     * Offers to tick a step of the hire's path off.
     *
     * The button names the step, and the tool result tells the mentor to *ask* rather than to
     * announce: the one thing this must never become is a mentor that marks work done because the
     * conversation went well. Only the hire knows whether they did it.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val userId = context.hire.value
        val stepId = call.uuidArgument("step_id")
            ?: return notProposed(
                "No step_id was provided. Read get_my_onboarding_path and pass the step_id of the " +
                    "step they mean.",
            )
        val step = myPathTool.findStep(userId, stepId)
            ?: return notProposed(
                "No step of this hire's own path has that id. Read get_my_onboarding_path again — an " +
                    "id from anywhere else is not theirs to tick off.",
            )

        refusalFor(step)?.let { return notProposed(it) }

        return proposalFor(step)
    }

    private fun refusalFor(step: GetOnboardingStepsResponse): String? = when {
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

    private fun proposalFor(step: GetOnboardingStepsResponse): HireActionOutcome {
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

        return HireActionOutcome(
            toolResult = "Proposed to the hire: mark “${step.title}” as done. They see a confirm " +
                "button and nothing changes unless they click it. Ask whether they have actually " +
                "done it — never say that it is done.$withdraws",
            proposal = HireActionProposal(
                action = COMPLETE_STEP,
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
     * Ticks the confirmed step off.
     *
     * The lock is checked again here, because the route underneath does not check it — the hire's
     * page enforces it by never offering the button — and a proposal can sit in a conversation long
     * enough for the path around it to change. Finished and skipped stay with the route, which
     * refuses both with a sentence of its own.
     */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val stepId = request.stepId
        if (stepId == null) {
            return BuddyActionResponse(ok = false, message = "No step was proposed to complete.")
        }
        val current = myPathTool.findStep(context.hire.value, stepId)
            ?: return BuddyActionResponse(ok = false, message = "That step isn't on your path.")
        if (current.locked) {
            return BuddyActionResponse(
                ok = false,
                message = "“${current.title}” is locked right now — something it waits on isn't finished yet.",
            )
        }
        val step = hirePathActionsApi.completeMyStep(authId, StepId(stepId))
        return BuddyActionResponse(
            ok = true,
            message = "Ticked off “${step.title}”. It is done on your path — if that was too early, " +
                "you can reopen it there.",
        )
    }

    companion object {
        const val COMPLETE_STEP = "complete_step"

        val SPEC = BuddyToolSpecDto(
            name = COMPLETE_STEP,
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
    }
}
