package com.sprintstart.sprintstartbackend.board.assistant

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
import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Offers to tick lines on a checklist the hire says they have finished.
 *
 * The hire's own statement about their own work, written to their own card, behind the same confirm as
 * everything else here — which is what makes it theirs rather than the mentor's verdict. It is offered
 * when they *say* they have done something, never concluded from the conversation: a board that ticks
 * itself because a model read something into a sentence is a board nobody can trust the state of.
 *
 * Setting only. Un-ticking would be somebody else deciding the hire was wrong about their own work, and
 * the checkbox on the card is right there.
 */
@Component
@Order(HireActionOrder.TICK_CHECKLIST_ITEMS)
class TickChecklistItemsAction(
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "tick something off your board"

    /**
     * Same shape as an amendment and the same refusals, because it is the same card and the same risk of
     * naming one that is not theirs. What differs is where the lines come from: an amendment's are the
     * mentor's, these are the hire's own — quoted back from the card so they can see which ones before
     * agreeing.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val cardId = call.uuidArgument("card_id") ?: return refused(NO_CHECKLIST_ID)
        val items = call.textListArgument("items")
        if (items.isEmpty()) return refused("No lines were named to tick off.")
        return offer(
            context,
            HireActionProposal(
                action = TICK_CHECKLIST_ITEMS,
                label = LABEL,
                question = null,
                cardId = cardId,
                checklistItems = items.take(MAX_CHECKLIST_ITEMS),
            ),
        )
    }

    /** Ticks the named lines, and can do nothing else to the card. */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val card = request.cardId?.let(::BoardCardId)
            ?: return BuddyActionResponse(ok = false, message = "No card was proposed to tick off.")
        val lines = request.checklistItems
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was nothing left to tick off.")
        }

        return try {
            when (val ticked = boardActions.tickChecklistItems(context.board, card, lines)) {
                0 -> BuddyActionResponse(
                    ok = false,
                    // Says which of the two it was, because they need different answers: one is
                    // "you already did that", the other is "I named the wrong line".
                    message = "Nothing changed — either those lines are already ticked, or they " +
                        "are not the ones on that card.",
                )
                else -> BuddyActionResponse(
                    ok = true,
                    message = "Ticked $ticked off. Nothing else on the list changed.",
                )
            }
        } catch (ex: ResponseStatusException) {
            BuddyActionResponse(ok = false, message = ex.reason ?: "That list could not be ticked.")
        }
    }

    companion object {
        const val TICK_CHECKLIST_ITEMS = "tick_checklist_items"
        const val LABEL = "Tick these off"

        val SPEC = BuddyToolSpecDto(
            name = TICK_CHECKLIST_ITEMS,
            description = "Offer to tick lines off a checklist of theirs, when the hire SAYS they " +
                "have done them. 'I have done the first two' is this tool. Read read_board for the " +
                "card's id and the exact lines, and pass the lines back word for word — they are " +
                "matched by their words, so a paraphrase ticks nothing. " +
                "Only ever when they tell you. Never conclude from the conversation that a step " +
                "looks done, never tick something as a side effect of answering, and never tick " +
                "the last line to tidy a list up: a board that ticks itself because a model read " +
                "something into a sentence is a board whose state nobody can trust. It cannot " +
                "un-tick, so if they say they were wrong, tell them the checkbox on the card is " +
                "theirs to click. This does NOT change anything by itself; they see a confirm " +
                "button naming the lines.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("card_id") {
                        put("type", "string")
                        put("description", "The checklist card's id, exactly as read_board gave it.")
                    }
                    putJsonObject("items") {
                        put("type", "array")
                        put(
                            "description",
                            "The lines to tick, word for word as read_board shows them on the card.",
                        )
                        putJsonObject("items") { put("type", "string") }
                    }
                }
                putJsonArray("required") {
                    add("card_id")
                    add("items")
                }
            },
        )
    }
}
