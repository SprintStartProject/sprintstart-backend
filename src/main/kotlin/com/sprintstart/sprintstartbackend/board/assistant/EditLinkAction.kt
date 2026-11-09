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
import com.sprintstart.sprintstartbackend.board.external.BoardCardVersion
import com.sprintstart.sprintstartbackend.board.external.BoardReading
import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.board.external.HireBoardApi
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Offers to change where one of the hire's links points, or what it is called. */
@Component
@Order(HireActionOrder.EDIT_LINK)
class EditLinkAction(
    private val hireBoard: HireBoardApi,
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "update a link on your board"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val url = call.textArgument("url")
        val label = call.textArgument("label").take(MAX_LABEL).ifBlank { null }
        val card = when (val found = hireBoard.ownCard(call, context.board, BoardCardKind.LINK)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        if (!url.isWebAddress()) return refused(NOT_A_WEB_ADDRESS)
        // The edit replaces the card whole, so a name left out would be a name deleted — and a
        // model retargeting a link rarely thinks to repeat what the link is called.
        val keptLabel = label ?: (card.content as? LinkContent)?.label

        return offer(
            context,
            HireActionProposal(
                action = EDIT_LINK,
                label = LABEL,
                question = null,
                cardId = card.id,
                linkUrl = url,
                linkLabel = keptLabel,
                basedOn = BoardCardVersion.of(card.content),
                preview = "Change your link “${BoardReading.nameOf(card)}” to " +
                    "${keptLabel?.let { "$it — " }.orEmpty()}$url",
            ),
        )
    }

    /** Writes the confirmed link, unless the card has moved on since the proposal (see [EditNoteAction]). */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val cardId = request.cardId
        val url = request.linkUrl?.trim().orEmpty()
        if (cardId == null || !url.isWebAddress()) {
            return BuddyActionResponse(ok = false, message = "There was no link change to make.")
        }
        boardActions.editCard(context.board, BoardCardId(cardId), linkRequest(url, request.linkLabel), request.basedOn)
        return EDITED
    }

    companion object {
        const val EDIT_LINK = "edit_link"
        const val LABEL = "Update this link"

        val SPEC = BuddyToolSpecDto(
            name = EDIT_LINK,
            description = "Offer to change where one of the hire's links points or what it is " +
                "called, when they ask. Read read_board for the card's id. Pass the full address " +
                "even if only the name changes. They see a confirm button before anything changes.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("card_id", cardIdParam())
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "The full address, starting with https:// or http://.")
                    }
                    putJsonObject("label") {
                        put("type", "string")
                        put("description", "What to call it on the card. Leave it out to keep the current name.")
                    }
                }
                putJsonArray("required") {
                    add("card_id")
                    add("url")
                }
            },
        )
    }
}
