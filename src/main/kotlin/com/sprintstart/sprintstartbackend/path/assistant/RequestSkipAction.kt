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
import com.sprintstart.sprintstartbackend.path.model.request.skip.CreateOnboardingSkipRequest
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

/**
 * The buddy's skip request: offering to ask the hire's PM to let them skip a step, and sending the
 * request once they confirm.
 *
 * The request goes through
 * the hire's own skip route, which owns every rule about it; the PM decides.
 */
@Component
@Order(HireActionOrder.REQUEST_SKIP)
class RequestSkipAction(
    private val myPathTool: MyPathTool,
    private val hirePathActionsApi: HirePathActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PERSON
    override val gerund = "ask your PM to skip a step"

    override fun isMountedFor(hire: UserId): Boolean = myPathTool.hasPath(hire.value)

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
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val user = context.hire
        val stepId = call.uuidArgument("step_id")
            ?: return notProposed(
                "No step_id was provided. Read get_my_onboarding_path and pass the step_id of the " +
                    "step they want to skip.",
            )
        val step = myPathTool.findStep(user.value, stepId)
            ?: return notProposed(
                "No step of this hire's own path has that id. Read get_my_onboarding_path again.",
            )
        val reason = call.textArgument("reason")
        refusalFor(step, reason)?.let { return notProposed(it) }

        return HireActionOutcome(
            toolResult = "Proposed to the hire: ask their PM to skip “${step.title}”, with the reason " +
                "“$reason”. They see a confirm button showing that reason, and nothing is sent unless " +
                "they click it. Their PM decides; until then the step stays on their path, and if it " +
                "is accepted it counts as done and unlocks what waits on it. Do not promise it will " +
                "be accepted.${declinedBefore(step)}",
            proposal = HireActionProposal(
                action = REQUEST_SKIP,
                label = "Ask your PM to skip “${step.title}”",
                question = null,
                stepId = step.id,
                reason = reason,
            ),
        )
    }

    private fun refusalFor(step: GetOnboardingStepsResponse, reason: String): String? = when {
        step.status == StepStatus.FINISHED ->
            "“${step.title}” is already done, so there is nothing to skip."
        step.status == StepStatus.SKIPPED ->
            "“${step.title}” is already skipped — their PM accepted it."
        step.hasPendingSkip() ->
            "They already asked to skip “${step.title}” and their PM has not decided yet. A second " +
                "request cannot be sent; they can change the reason on the step's own page " +
                "(${MyPathTool.STEP_PAGE_LINK}${step.id})."
        reason.isBlank() ->
            "No reason was provided. Their PM decides on the reason, so ask why they want to skip " +
                "it — already know it, not relevant to their role, covered elsewhere — and put " +
                "that into a sentence or two before offering this again."
        else -> null
    }

    /**
     * What the mentor is told when the PM declined an earlier request for [step].
     *
     * A declined request can be asked again, and the PM said why the first time. The new reason
     * should answer that, or it will be declined for the same thing.
     */
    private fun declinedBefore(step: GetOnboardingStepsResponse): String =
        step.skip
            ?.takeIf { it.accepted == false }
            ?.let { declined ->
                " Their PM declined an earlier request" +
                    declined.reviewComment
                        ?.takeIf { it.isNotBlank() }
                        ?.let { " with: “$it”" }
                        .orEmpty() +
                    " — make sure this reason addresses that."
            }.orEmpty()

    /** Files the confirmed skip request through the hire's own route, which owns every rule about it. */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val user = context.hire
        val stepId = request.stepId
        val reason = request.reason
        if (stepId == null || reason.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "No skip request was proposed to send.")
        }
        val step = myPathTool.findStep(user.value, stepId)
            ?: return BuddyActionResponse(ok = false, message = "That step isn't on your path.")

        hirePathActionsApi.requestSkip(authId, StepId(stepId), CreateOnboardingSkipRequest(reason = reason))
        return BuddyActionResponse(
            ok = true,
            message = "Sent — your PM will decide on skipping “${step.title}”. Until then it stays on " +
                "your path; you can change or withdraw the reason on the step's page.",
        )
    }

    companion object {
        const val REQUEST_SKIP = "request_skip"

        val SPEC = BuddyToolSpecDto(
            name = REQUEST_SKIP,
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
    }
}
