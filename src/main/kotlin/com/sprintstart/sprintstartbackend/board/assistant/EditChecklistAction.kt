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
import com.sprintstart.sprintstartbackend.board.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.board.model.request.board.ChecklistItemRequest
import com.sprintstart.sprintstartbackend.board.model.response.board.ChecklistContent
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
 * Offers a whole new version of one of the hire's checklists — its title and its lines — when they ask
 * for it tidied.
 *
 * Broader than [AmendChecklistAction] and [RewordChecklistItemAction] on purpose, and still gated the same
 * way: the confirm shows the list as it would read, so a dropped or reworded line is one the hire saw go.
 * Lines that survive keep their ticks.
 */
@Component
@Order(HireActionOrder.EDIT_CHECKLIST)
class EditChecklistAction(
    private val hireBoard: HireBoardApi,
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "update a list on your board"

    /**
     * The preview names what would be *lost* as well as what the list would say, because a line that
     * silently drops out of a long list is the one change a hire reading the new version would not
     * notice. Lines that survive keep their ticks — `HireBoardActionsApi.editCard` matches them back by
     * their words.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val title = call.textArgument("title").take(MAX_CHECKLIST_TITLE).ifBlank { null }
        val items = call.textListArgument("items").map { it.take(MAX_CHECKLIST_ITEM_LENGTH) }.take(MAX_CHECKLIST_ITEMS)
        val card = when (val found = hireBoard.ownCard(call, context.board, BoardCardKind.CHECKLIST)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        if (items.isEmpty()) {
            return refused(
                "No lines were given. To take the whole list off their board, use dismiss_cards " +
                    "instead of emptying it.",
            )
        }

        val dropped = droppedLines((card.content as? ChecklistContent)?.items?.map { it.text }.orEmpty(), items)
        val preview = buildString {
            append("Update your list “${BoardReading.nameOf(card)}” to the ${items.size} lines shown.")
            if (dropped.isNotEmpty()) {
                append(" Lines that would go: ")
                append(dropped.joinToString("; ") { "“$it”" })
                append(".")
            }
            append(" Lines that stay keep their ticks.")
        }

        return offer(
            context,
            HireActionProposal(
                action = EDIT_CHECKLIST,
                label = LABEL,
                question = null,
                cardId = card.id,
                checklistTitle = title,
                checklistItems = items,
                basedOn = BoardCardVersion.of(card.content),
                preview = preview,
            ),
        )
    }

    /**
     * The existing lines an edit to [proposed] would remove, counted the way the edit itself matches
     * them (`keepingLinesOf`): trimmed, case-insensitive, and each proposed line claiming at most one
     * existing line. So a list with a line twice, edited to have it once, says one copy goes.
     */
    private fun droppedLines(existing: List<String>, proposed: List<String>): List<String> {
        val unclaimed = proposed.groupingBy { it.trim().lowercase() }.eachCount().toMutableMap()
        return existing.filter { line ->
            val key = line.trim().lowercase()
            val left = unclaimed[key] ?: 0
            if (left > 0) unclaimed[key] = left - 1
            left == 0
        }
    }

    /**
     * Writes the confirmed list, unless the card has moved on since the proposal (see [EditNoteAction]).
     * Re-capped here rather than trusted from the confirm, as every free-text payload is.
     */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val cardId = request.cardId
        val lines = cappedLines(request.checklistItems)
        if (cardId == null || lines.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was no list change to make.")
        }
        val checklist = ChecklistCardRequest(
            title = request.checklistTitle
                ?.trim()
                ?.take(MAX_CHECKLIST_TITLE)
                ?.ifBlank { null },
            items = lines.map { ChecklistItemRequest(text = it) },
        )
        boardActions.editCard(context.board, BoardCardId(cardId), checklist, request.basedOn)
        return EDITED
    }

    companion object {
        const val EDIT_CHECKLIST = "edit_checklist"
        const val LABEL = "Update this list"

        val SPEC = BuddyToolSpecDto(
            name = EDIT_CHECKLIST,
            description = "Offer a new version of one of the hire's checklists — its title and its " +
                "whole list of lines — when they ask you to tidy it, merge steps, drop ones that no " +
                "longer apply, or put it in a better order. Pass EVERY line the list should have " +
                "afterwards, word for word for the ones that stay: a line you leave out is removed, " +
                "and a line keeps its tick only if its words are unchanged. For just adding steps " +
                "use amend_checklist, for rewording one line use reword_checklist_item, and for " +
                "ticking use tick_checklist_items — those are smaller changes and easier to agree " +
                "to. The confirm shows the new list and names every line that would go.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("card_id", cardIdParam())
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "The list's heading. Pass the current one to keep it.")
                    }
                    putJsonObject("items") {
                        put("type", "array")
                        put("description", "Every line the list should have, in order.")
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
