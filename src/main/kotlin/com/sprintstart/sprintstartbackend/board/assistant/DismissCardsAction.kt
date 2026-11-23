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
import com.sprintstart.sprintstartbackend.board.external.BoardReading
import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.board.external.HireBoardApi
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Offers to take cards off the hire's board — "clean up my board".
 *
 * Non-destructive, like the hire's own dismissal: the rows survive, so each card is a state flip away from
 * coming back. The confirm names every card that would go, by the board's own names for them.
 */
@Component
@Order(HireActionOrder.DISMISS_CARDS)
class DismissCardsAction(
    private val hireBoard: HireBoardApi,
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "clear cards off your board"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val cards = when (val found = hireBoard.namedCards(call, context.board)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        val names = cards.map { BoardReading.nameOf(it) }

        return offer(
            context,
            HireActionProposal(
                action = DISMISS_CARDS,
                label = LABEL,
                question = null,
                cardIds = cards.map { it.id },
                cardNames = names,
                preview = "Take ${cards.size} ${if (cards.size == 1) "card" else "cards"} off your " +
                    "board: ${names.joinToString(", ")}. Nothing is deleted — each can be brought back.",
            ),
        )
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val ids = request.cardIds
            .orEmpty()
            .take(MAX_CARDS)
            .map(::BoardCardId)
        if (ids.isEmpty()) return BuddyActionResponse(ok = false, message = "No cards were proposed to remove.")

        return when (val gone = boardActions.dismiss(context.board, ids)) {
            0 -> BuddyActionResponse(ok = false, message = "Nothing changed — those cards are already off your board.")
            else -> BuddyActionResponse(
                ok = true,
                message = "Took $gone off your board. Nothing was deleted, so any of them can come back.",
            )
        }
    }

    companion object {
        const val DISMISS_CARDS = "dismiss_cards"
        const val LABEL = "Remove these from your board"

        val SPEC = BuddyToolSpecDto(
            name = DISMISS_CARDS,
            description = "Offer to take cards off the hire's board — 'clean up my board', 'get rid " +
                "of the old notes', 'I don't need that card any more'. Any card can go, theirs " +
                "included. Read read_board first and pass the ids of exactly the cards to remove; " +
                "when they ask you to clean up, pick the ones that are finished, stale or " +
                "duplicated and say why in your reply. Only when they ask: never clear things off " +
                "their board as a side effect of something else. Nothing is deleted — every card " +
                "can be brought back — and the confirm names each card that would go.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("card_ids", cardIdsParam("The ids of the cards to take off, as read_board gave them."))
                }
                putJsonArray("required") { add("card_ids") }
            },
        )
    }
}
