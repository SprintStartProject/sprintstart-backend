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
 * Offers to add lines to a checklist the hire already has.
 *
 * The half of [PlaceChecklistAction] that stops the board filling with near-duplicates: a hire who
 * finishes two steps and asks what comes next should get the answer on the card they are already
 * ticking, not on a second one beside it.
 *
 * Append-only, and that is enforced in `HireBoardActionsApi.appendChecklistItems` rather than asked of
 * the mentor. Their existing lines come back untouched — same words, same ids, same ticks — and the
 * confirm shows only what would be added, because a change you cannot see is one you cannot agree to.
 */
@Component
@Order(HireActionOrder.AMEND_CHECKLIST)
class AmendChecklistAction(
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "add to a checklist on your board"

    /**
     * Takes only the *new* lines, never the whole list. The mentor is not asked to send back what is
     * already on the card, so there is no version of this where it quietly rewords or drops one.
     *
     * The card id comes from `read_board`, which is the only place the mentor learns that a checklist
     * exists at all.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val cardId = call.uuidArgument("card_id") ?: return refused(NO_CHECKLIST_ID)
        val items = call.textListArgument("items")
        if (items.isEmpty()) return refused("No lines were provided to add.")
        return offer(
            context,
            HireActionProposal(
                action = AMEND_CHECKLIST,
                label = LABEL,
                question = null,
                cardId = cardId,
                checklistItems = items.take(MAX_CHECKLIST_ITEMS),
            ),
        )
    }

    /** Adds the proposed lines to the hire's card, and can do nothing else to it. */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val card = request.cardId?.let(::BoardCardId)
            ?: return BuddyActionResponse(ok = false, message = "No card was proposed to add to.")
        val lines = cappedLines(request.checklistItems)
        if (lines.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was nothing left to add.")
        }

        return try {
            boardActions.appendChecklistItems(context.board, card, lines)
            BuddyActionResponse(
                ok = true,
                message = "Added ${lines.size} to that list. Nothing else on it changed.",
            )
        } catch (ex: ResponseStatusException) {
            // A card that is not theirs, or not a checklist. Both are things the hire can see for
            // themselves, so they come back as the sentence rather than as a failed confirm.
            BuddyActionResponse(ok = false, message = ex.reason ?: "That list could not be added to.")
        }
    }

    companion object {
        const val AMEND_CHECKLIST = "amend_checklist"
        const val LABEL = "Add these to the list"

        val SPEC = BuddyToolSpecDto(
            name = AMEND_CHECKLIST,
            description = "Offer to add steps to a checklist the hire ALREADY has, instead of " +
                "making a second card beside it. WHENEVER you answer what comes next, check " +
                "whether it belongs on a list they already have — it usually does. 'I have done " +
                "the first two, what now?' is this tool, every time: answering in prose alone " +
                "leaves them holding something that is gone by tomorrow, beside a card that still " +
                "says seven things and gives no hint which. Having the list in this conversation " +
                "is not the same as it being on their board, and is never a reason to skip the " +
                "board — read read_board first and pass that card's id. " +
                "Pass ONLY the new lines — never the ones already on the card. You " +
                "cannot reword, re-order or remove what is there, and you should not try: those " +
                "lines are the hire's, even the ones you suggested, and the card is what they are " +
                "working from. This does NOT write anything by itself; they see a confirm button " +
                "showing only what would be added.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("card_id") {
                        put("type", "string")
                        put("description", "The checklist card's id, exactly as read_board gave it.")
                    }
                    putJsonObject("items") {
                        put("type", "array")
                        put("description", "Only the new lines, in the order they should be done.")
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
