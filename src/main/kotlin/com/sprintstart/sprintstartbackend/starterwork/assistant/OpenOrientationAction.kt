package com.sprintstart.sprintstartbackend.starterwork.assistant

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
import com.sprintstart.sprintstartbackend.assistant.external.noArgumentsSchema
import com.sprintstart.sprintstartbackend.assistant.external.offeredToHire
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import com.sprintstart.sprintstartbackend.starterwork.model.response.orientation.MyOrientationResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Offers to open the cited, step-by-step packet for the task the hire is on. */
@Component
@Order(HireActionOrder.OPEN_ORIENTATION)
class OpenOrientationAction(
    private val hireStarterWork: HireStarterWorkApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "open a task packet"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome =
        HireActionOutcome(
            toolResult = offeredToHire(context.requireProject.name, LABEL),
            proposal = HireActionProposal(action = OPEN_ORIENTATION, label = LABEL, question = null),
        )

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val orientation = hireStarterWork.orientationFor(context.hire, context.requireProject.id)
        return if (orientation.packet != null) {
            BuddyActionResponse(
                ok = true,
                message = "I've put together your task orientation for “${orientation.taskTitle}” — " +
                    "the step-by-step, cited guide is right here in our conversation.",
            )
        } else {
            BuddyActionResponse(ok = false, message = noPacketReason(orientation))
        }
    }

    /**
     * Why there is no packet, said only as far as this knows.
     *
     * Three different states, three sentences, because each is a claim about the hire's own work: telling a
     * hire whose packet failed to assemble that they have no current task contradicts their own board.
     * Being told a false thing about your own state is worse than being told nothing — it is the hire's word
     * against the system's, and the hire stops trusting the surface rather than the sentence.
     *
     * So the no-task line is only for a [MyOrientationResponse.taskId] that really is null. With a task and
     * no packet, the answer names the task and says the packet is what is missing, which is also the
     * difference between "claim something" and "try again".
     */
    private fun noPacketReason(orientation: MyOrientationResponse): String = when {
        orientation.reason != null -> orientation.reason
        orientation.taskId == null ->
            "There's no current task to open a packet for yet — claim one and I'll put it together."
        else ->
            "I couldn't put a packet together for “${orientation.taskTitle}” just now. " +
                "Nothing is wrong with the task — ask me again in a moment."
    }

    companion object {
        const val OPEN_ORIENTATION = "open_orientation"
        const val LABEL = "Open the task packet"

        val SPEC = BuddyToolSpecDto(
            name = OPEN_ORIENTATION,
            description = "Offer to assemble the task orientation packet for the task the hire claimed — a " +
                "step-by-step, cited guide to setting up, finding the code, making the change, and opening the " +
                "PR. Proposes only; the hire confirms. Use when they ask how to start the task they have. Takes " +
                "no arguments.",
            parameters = noArgumentsSchema(),
        )
    }
}
