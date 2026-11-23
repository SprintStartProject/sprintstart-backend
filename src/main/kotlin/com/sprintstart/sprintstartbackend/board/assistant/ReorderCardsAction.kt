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

/** Offers to put the hire's cards in a new order. The confirm shows the order it would leave. */
@Component
@Order(HireActionOrder.REORDER_CARDS)
class ReorderCardsAction(
    private val hireBoard: HireBoardApi,
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "rearrange your board"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val cards = when (val found = hireBoard.namedCards(call, context.board)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        val names = cards.map { BoardReading.nameOf(it) }

        return offer(
            context,
            HireActionProposal(
                action = REORDER_CARDS,
                label = LABEL,
                question = null,
                cardIds = cards.map { it.id },
                cardNames = names,
                preview = "Put these first on your board, in this order: ${names.joinToString(", ")}. " +
                    "Everything else keeps its order after them.",
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
        if (ids.isEmpty()) return BuddyActionResponse(ok = false, message = "No order was proposed.")

        return when (val moved = boardActions.reorder(context.board, ids)) {
            0 -> BuddyActionResponse(ok = true, message = "Your board was already in that order.")
            else -> BuddyActionResponse(ok = true, message = "Rearranged — $moved cards moved.")
        }
    }

    companion object {
        const val REORDER_CARDS = "reorder_cards"
        const val LABEL = "Rearrange your board"

        val SPEC = BuddyToolSpecDto(
            name = REORDER_CARDS,
            description = "Offer to rearrange the hire's board, when they ask — 'put the things I " +
                "need this week at the top'. Pass the ids of the cards that should come first, in " +
                "the order they should come; every card you leave out keeps its order after them. " +
                "Read read_board for the ids. The confirm shows the order that would result.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put(
                        "card_ids",
                        cardIdsParam("The ids of the cards to put first, in order, as read_board gave them."),
                    )
                }
                putJsonArray("required") { add("card_ids") }
            },
        )
    }
}
