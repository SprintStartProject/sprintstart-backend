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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Offers to keep a link on the hire's board — the last of the three kinds a hire writes themselves. */
@Component
@Order(HireActionOrder.PLACE_LINK)
class PlaceLinkAction(
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "keep a link on your board"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val url = call.textArgument("url")
        val label = call.textArgument("label").take(MAX_LABEL).ifBlank { null }
        if (!url.isWebAddress()) return refused(NOT_A_WEB_ADDRESS)

        return offer(
            context,
            HireActionProposal(
                action = PLACE_LINK,
                label = LABEL,
                question = null,
                linkUrl = url,
                linkLabel = label,
                preview = "Add a link to your board: ${label?.let { "$it — " }.orEmpty()}$url",
            ),
        )
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val url = request.linkUrl?.trim().orEmpty()
        if (!url.isWebAddress()) return BuddyActionResponse(ok = false, message = "There was no link left to keep.")

        boardActions.addCard(context.board, linkRequest(url, request.linkLabel))
        return BuddyActionResponse(ok = true, message = "Kept on your board. It's yours — edit it as you like.")
    }

    companion object {
        const val PLACE_LINK = "place_link"
        const val LABEL = "Keep this link"

        val SPEC = BuddyToolSpecDto(
            name = PLACE_LINK,
            description = "Offer to keep a link on the hire's board — a doc, a dashboard, a ticket " +
                "they will want again. Only web addresses, and only ones you actually have from " +
                "this conversation or your tools: never guess an address. This does NOT write " +
                "anything by itself; they see a confirm button with the link on it.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "The full address, starting with https:// or http://.")
                    }
                    putJsonObject("label") {
                        put("type", "string")
                        put("description", "What to call it on the card, in a few words. Optional.")
                    }
                }
                putJsonArray("required") { add("url") }
            },
        )
    }
}
