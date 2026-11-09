package com.sprintstart.sprintstartbackend.knowledge.assistant

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
import com.sprintstart.sprintstartbackend.assistant.external.offeredToHire
import com.sprintstart.sprintstartbackend.knowledge.external.HireKnowledgeApi
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Offers to pass something from the hire to their project's PM, and files it as a knowledge request once
 * they confirm.
 *
 * The question reaches the PM through [HireKnowledgeApi.escalate], the same route the hire's own "ask my PM"
 * takes, so the answer comes back into the conversation the way every other escalation's does.
 */
@Component
@Order(HireActionOrder.FLAG_TO_PM)
class FlagToPmAction(
    private val hireKnowledge: HireKnowledgeApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "flag this to a PM"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val question = call.textArgument("question")
        if (question.isBlank()) {
            return HireActionOutcome(
                "No question was provided to flag. Ask the hire what they want answered first.",
                null,
            )
        }
        return HireActionOutcome(
            toolResult = offeredToHire(context.requireProject.name, LABEL),
            proposal = HireActionProposal(action = FLAG_TO_PM, label = LABEL, question = question),
        )
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val question = request.question?.trim().orEmpty()
        if (question.isBlank()) {
            return BuddyActionResponse(ok = false, message = "I need the question to flag — tell me what to ask.")
        }
        hireKnowledge.escalate(authId, context.requireProject.id, question)
        return BuddyActionResponse(
            ok = true,
            message = "Flagged to your PM. Their answer will show up here once they reply.",
        )
    }

    companion object {
        const val FLAG_TO_PM = "flag_to_pm"
        const val LABEL = "Flag this to your PM"

        val SPEC = BuddyToolSpecDto(
            name = FLAG_TO_PM,
            description = "Offer to pass something from the hire to their project's PM. Two cases: " +
                "(1) the hire ASKS you to flag, raise or pass something to their PM — a question, a problem, a " +
                "blocker, feedback on their path. Their asking is the reason: offer it straight away, and never " +
                "decide for them that it is not a PM matter or that the docs answer it first. " +
                "(2) Neither the docs nor the canonical answers cover a question, as the last resort when you " +
                "genuinely cannot ground an answer. " +
                "This does NOT send anything — it shows the hire a confirm button, and only they can send it. " +
                "Put what should reach the PM in `question`, phrased clearly and in the hire's sense.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("question") {
                        put("type", "string")
                        put(
                            "description",
                            "What to send to the PM — a question, or what the hire wants them to know — phrased " +
                                "clearly for a person to answer.",
                        )
                    }
                }
                putJsonArray("required") { add("question") }
            },
        )
    }
}
