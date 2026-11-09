package com.sprintstart.sprintstartbackend.arrival.assistant

import com.sprintstart.sprintstartbackend.arrival.external.HireArrivalApi
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
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Offers to ask a named colleague to confirm a piece of the hire's work, and files the request once they
 * confirm.
 *
 * The buddy relays; it never confirms. Every rule that makes an attestation worth anything — not the hire,
 * on this project — is enforced by [HireArrivalApi.requestAttestation], so a buddy that got the person wrong
 * produces a legible refusal rather than weak evidence.
 */
@Component
@Order(HireActionOrder.REQUEST_ATTESTATION)
class RequestAttestationAction(
    private val hireArrival: HireArrivalApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "ask somebody to confirm your work"

    /**
     * Both halves must be real before the hire sees a button: what work, and which teammate. A missing one
     * comes back as guidance the buddy can act on rather than a proposal it cannot honour.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val title = call.textArgument("title")
        val attesterId = call.uuidArgument("attester_id")
        return when {
            title.isBlank() ->
                HireActionOutcome("No title was provided. Ask the hire what work they want confirmed.", null)
            attesterId == null ->
                HireActionOutcome(
                    "No attester_id was provided. Read get_teammates and ask the hire which of " +
                        "them should confirm it, then pass that person's id.",
                    null,
                )
            else -> HireActionOutcome(
                toolResult = offeredToHire(context.requireProject.name, LABEL),
                proposal = HireActionProposal(
                    action = REQUEST_ATTESTATION,
                    label = LABEL,
                    question = null,
                    title = title,
                    attesterId = attesterId.toString(),
                ),
            )
        }
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val title = request.title
        val attesterId = request.attesterId
        if (title.isNullOrBlank() || attesterId == null) {
            return BuddyActionResponse(ok = false, message = "I need to know what work to confirm and who to ask.")
        }
        return try {
            val stored = hireArrival.requestAttestation(
                hireId = context.hire,
                projectId = context.requireProject.id,
                title = title,
                attesterId = UserId(attesterId),
            )
            BuddyActionResponse(
                ok = true,
                message = "Asked them to confirm “$stored”. " +
                    "It counts once they do — you will see it in what you have shown.",
            )
        } catch (e: ResponseStatusException) {
            // A handled precondition ("not on this project", "that is you") is a sentence the buddy
            // can relay, not a stack trace.
            BuddyActionResponse(ok = false, message = e.reason ?: "That request could not be filed.")
        }
    }

    companion object {
        const val REQUEST_ATTESTATION = "request_attestation"
        const val LABEL = "Ask them to confirm this"

        val SPEC = BuddyToolSpecDto(
            name = REQUEST_ATTESTATION,
            description = "Offer to ask a named colleague to confirm a piece of work the hire has " +
                "done — a facilitated ceremony, a published plan, anything no system can see. " +
                "This does NOT confirm anything by itself; it shows the hire a confirm button and " +
                "runs only if they click, and the colleague still has to agree. Use for work that " +
                "leaves no trace a tool can read. Read get_teammates first and ask the hire who " +
                "should confirm it — never guess, and never name the hire themselves.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "What the hire did, in their words.")
                    }
                    putJsonObject("attester_id") {
                        put("type", "string")
                        put("description", "The user id of the teammate the hire picked to confirm it.")
                    }
                }
                putJsonArray("required") {
                    add("title")
                    add("attester_id")
                }
            },
        )
    }
}
