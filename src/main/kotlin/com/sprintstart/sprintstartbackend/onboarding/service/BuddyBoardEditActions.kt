package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyActionType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistItemRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService.BuddyActionProposal
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService.ProposeOutcome
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyBoardWriteActions.BoardWritePayload
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyBoardWriteActions.Scope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The board actions that reach past adding: editing what the hire wrote, clearing cards off, and
 * rearranging what is left — "clean up my board", "fix this note".
 *
 * **The rule these rest on.** A board used to be the hire's in a strong sense: the mentor could add
 * to it and never touch what was there. It is now that the buddy may do anything the hire can,
 * on two conditions that replace the old exclusion rather than dropping it:
 *
 * - **Pre-approval.** Each of these only proposes. The confirm shows what would change — the new
 *   words, the cards that would go, the order that would result — and only the hire's click writes.
 * - **Attribution.** Every write lands through `BoardCard.recordChange` as the buddy's, dated, so
 *   the board can say afterwards that the buddy changed this card and when.
 *
 * Offered through [BuddyBoardWriteActions], which owns the confirm plumbing all board writes share;
 * split out because these six read the board before proposing — to name the cards a confirm is
 * about — where the rest only carry the model's words.
 *
 * **Names are resolved here, never taken from the model.** A dismissal the model described as "the
 * old onboarding note" but aimed at a different card id would be confirmed by a hire reading one
 * thing and agreeing to another. So every card a proposal names is looked up on the board, and the
 * name on the confirm is the board's own.
 */
@Component
@Suppress("TooManyFunctions") // Six actions, a propose and a perform each, and their shared readers.
class BuddyBoardEditActions(
    private val boardService: BoardService,
) {
    /** The six tools, offered alongside [BuddyBoardWriteActions]' own. */
    fun specs(): List<BuddyToolSpecDto> =
        listOf(
            PLACE_LINK_SPEC,
            EDIT_NOTE_SPEC,
            EDIT_LINK_SPEC,
            EDIT_CHECKLIST_SPEC,
            DISMISS_CARDS_SPEC,
            REORDER_CARDS_SPEC,
        )

    fun handles(type: BuddyActionType): Boolean = type in HANDLED

    /** Turns one of these tool calls into a proposal, or into the reason there is none. */
    fun propose(call: BuddyToolCallDto, type: BuddyActionType, scope: Scope): ProposeOutcome =
        when (type) {
            BuddyActionType.PLACE_LINK -> proposeLink(call, type, scope)
            BuddyActionType.EDIT_NOTE -> proposeNoteEdit(call, type, scope)
            BuddyActionType.EDIT_LINK -> proposeLinkEdit(call, type, scope)
            BuddyActionType.EDIT_CHECKLIST -> proposeChecklistEdit(call, type, scope)
            BuddyActionType.DISMISS_CARDS -> proposeDismissal(call, type, scope)
            BuddyActionType.REORDER_CARDS -> proposeOrder(call, type, scope)
            else -> error("$type is not a board edit; handles() keeps it out of here")
        }

    /**
     * Runs a confirmed one. Everything is re-checked against the board as it is *now* — the card
     * may have been dismissed, edited or moved since the proposal was made.
     */
    fun perform(type: BuddyActionType, userId: UUID, projectId: UUID, payload: BoardWritePayload): BuddyActionResponse =
        try {
            when (type) {
                BuddyActionType.PLACE_LINK -> placeLink(userId, projectId, payload)
                BuddyActionType.EDIT_NOTE -> editNote(userId, projectId, payload)
                BuddyActionType.EDIT_LINK -> editLink(userId, projectId, payload)
                BuddyActionType.EDIT_CHECKLIST -> editChecklist(userId, projectId, payload)
                BuddyActionType.DISMISS_CARDS -> dismissCards(userId, projectId, payload.cardIds)
                BuddyActionType.REORDER_CARDS -> reorderCards(userId, projectId, payload.cardIds)
                else -> error("$type is not a board edit; handles() keeps it out of here")
            }
        } catch (ex: ResponseStatusException) {
            // A card that is gone, not theirs, or not that kind any more. All things the hire can
            // see for themselves, so they come back as the sentence rather than a failed confirm.
            BuddyActionResponse(ok = false, message = ex.reason ?: "That change could not be made.")
        }

    // -- Proposing ---------------------------------------------------------------------------------

    private fun proposeLink(call: BuddyToolCallDto, type: BuddyActionType, scope: Scope): ProposeOutcome {
        val url = call.stringArg("url").trim()
        val label = call
            .stringArg("label")
            .trim()
            .take(MAX_LABEL)
            .ifBlank { null }
        if (!url.isWebAddress()) return refused(NOT_A_WEB_ADDRESS)

        return offer(
            type,
            scope,
            BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                linkUrl = url,
                linkLabel = label,
                preview = "Add a link to your board: ${label?.let { "$it — " }.orEmpty()}$url",
            ),
        )
    }

    private fun proposeNoteEdit(call: BuddyToolCallDto, type: BuddyActionType, scope: Scope): ProposeOutcome {
        val text = call.stringArg("text").trim().take(MAX_NOTE_LENGTH)
        val card = when (val found = ownCard(call, scope, BoardCardKind.NOTE)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        if (text.isEmpty()) return refused("No new text was given for the note.")
        // read_board shows only the start of a long note. A rewrite of the part the mentor saw would
        // drop the part it did not, and the hire would confirm a loss they could not see on the offer.
        if ((card.content as? NoteContent)?.text.orEmpty().length > BoardReading.NOTE_PREVIEW) {
            return refused(
                "That note is longer than read_board shows you, so a rewrite would lose the part you " +
                    "have not seen. Tell the hire what to change and let them edit it themselves.",
            )
        }

        return offer(
            type,
            scope,
            BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                cardId = card.id,
                noteText = text,
                preview = "Rewrite your note “${BoardReading.nameOf(card)}” so it reads as shown.",
            ),
        )
    }

    private fun proposeLinkEdit(call: BuddyToolCallDto, type: BuddyActionType, scope: Scope): ProposeOutcome {
        val url = call.stringArg("url").trim()
        val label = call
            .stringArg("label")
            .trim()
            .take(MAX_LABEL)
            .ifBlank { null }
        val card = when (val found = ownCard(call, scope, BoardCardKind.LINK)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        if (!url.isWebAddress()) return refused(NOT_A_WEB_ADDRESS)
        // The edit replaces the card whole, so a name left out would be a name deleted — and a
        // model retargeting a link rarely thinks to repeat what the link is called.
        val keptLabel = label ?: (card.content as? LinkContent)?.label

        return offer(
            type,
            scope,
            BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                cardId = card.id,
                linkUrl = url,
                linkLabel = keptLabel,
                preview = "Change your link “${BoardReading.nameOf(card)}” to " +
                    "${keptLabel?.let { "$it — " }.orEmpty()}$url",
            ),
        )
    }

    /**
     * Offers a whole new version of one of the hire's checklists.
     *
     * The preview names what would be *lost* as well as what the list would say, because a line
     * that silently drops out of a long list is the one change a hire reading the new version would
     * not notice. Lines that survive keep their ticks — `BoardService.editAuthoredCardForBuddy`
     * matches them back by their words.
     */
    private fun proposeChecklistEdit(call: BuddyToolCallDto, type: BuddyActionType, scope: Scope): ProposeOutcome {
        val title = call
            .stringArg("title")
            .trim()
            .take(MAX_CHECKLIST_TITLE)
            .ifBlank { null }
        val items = call.stringListArg("items").map { it.take(MAX_CHECKLIST_ITEM_LENGTH) }.take(MAX_CHECKLIST_ITEMS)
        val card = when (val found = ownCard(call, scope, BoardCardKind.CHECKLIST)) {
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
            type,
            scope,
            BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                cardId = card.id,
                checklistTitle = title,
                checklistItems = items,
                preview = preview,
            ),
        )
    }

    private fun proposeDismissal(call: BuddyToolCallDto, type: BuddyActionType, scope: Scope): ProposeOutcome {
        val cards = when (val found = namedCards(call, scope)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        val names = cards.map { BoardReading.nameOf(it) }

        return offer(
            type,
            scope,
            BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                cardIds = cards.map { it.id },
                cardNames = names,
                preview = "Take ${cards.size} ${if (cards.size == 1) "card" else "cards"} off your " +
                    "board: ${names.joinToString(", ")}. Nothing is deleted — each can be brought back.",
            ),
        )
    }

    private fun proposeOrder(call: BuddyToolCallDto, type: BuddyActionType, scope: Scope): ProposeOutcome {
        val cards = when (val found = namedCards(call, scope)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        val names = cards.map { BoardReading.nameOf(it) }

        return offer(
            type,
            scope,
            BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                cardIds = cards.map { it.id },
                cardNames = names,
                preview = "Put these first on your board, in this order: ${names.joinToString(", ")}. " +
                    "Everything else keeps its order after them.",
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

    // -- Performing --------------------------------------------------------------------------------

    private fun placeLink(userId: UUID, projectId: UUID, payload: BoardWritePayload): BuddyActionResponse {
        val url = payload.linkUrl?.trim().orEmpty()
        if (!url.isWebAddress()) return BuddyActionResponse(ok = false, message = "There was no link left to keep.")

        boardService.addAuthoredCard(userId, projectId, linkRequest(url, payload.linkLabel), by = BoardActor.BUDDY)
        return BuddyActionResponse(ok = true, message = "Kept on your board. It's yours — edit it as you like.")
    }

    private fun editNote(userId: UUID, projectId: UUID, payload: BoardWritePayload): BuddyActionResponse {
        val cardId = payload.cardId
        val text = payload.noteText
            ?.trim()
            ?.take(MAX_NOTE_LENGTH)
            .orEmpty()
        if (cardId == null || text.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was no note change to make.")
        }
        return edited(userId, projectId, cardId, NoteCardRequest(text = text))
    }

    private fun editLink(userId: UUID, projectId: UUID, payload: BoardWritePayload): BuddyActionResponse {
        val cardId = payload.cardId
        val url = payload.linkUrl?.trim().orEmpty()
        if (cardId == null || !url.isWebAddress()) {
            return BuddyActionResponse(ok = false, message = "There was no link change to make.")
        }
        return edited(userId, projectId, cardId, linkRequest(url, payload.linkLabel))
    }

    /** Re-capped here rather than trusted from the confirm, as every free-text payload is. */
    private fun editChecklist(userId: UUID, projectId: UUID, payload: BoardWritePayload): BuddyActionResponse {
        val cardId = payload.cardId
        val lines = payload.checklistItems
            .orEmpty()
            .map { it.trim().take(MAX_CHECKLIST_ITEM_LENGTH) }
            .filter { it.isNotBlank() }
            .take(MAX_CHECKLIST_ITEMS)
        if (cardId == null || lines.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was no list change to make.")
        }
        val request = ChecklistCardRequest(
            title = payload.checklistTitle
                ?.trim()
                ?.take(MAX_CHECKLIST_TITLE)
                ?.ifBlank { null },
            items = lines.map { ChecklistItemRequest(text = it) },
        )
        return edited(userId, projectId, cardId, request)
    }

    private fun edited(
        userId: UUID,
        projectId: UUID,
        cardId: UUID,
        request: AuthoredCardRequest,
    ): BuddyActionResponse {
        boardService.editAuthoredCardForBuddy(userId, projectId, cardId, request)
        return BuddyActionResponse(
            ok = true,
            message = "Updated. The card shows it was your buddy's change, and it's still yours to edit.",
        )
    }

    private fun dismissCards(userId: UUID, projectId: UUID, cardIds: List<UUID>?): BuddyActionResponse {
        val ids = cardIds.orEmpty().take(MAX_CARDS)
        if (ids.isEmpty()) return BuddyActionResponse(ok = false, message = "No cards were proposed to remove.")

        return when (val gone = boardService.dismissForBuddy(userId, projectId, ids)) {
            0 -> BuddyActionResponse(ok = false, message = "Nothing changed — those cards are already off your board.")
            else -> BuddyActionResponse(
                ok = true,
                message = "Took $gone off your board. Nothing was deleted, so any of them can come back.",
            )
        }
    }

    private fun reorderCards(userId: UUID, projectId: UUID, cardIds: List<UUID>?): BuddyActionResponse {
        val ids = cardIds.orEmpty().take(MAX_CARDS)
        if (ids.isEmpty()) return BuddyActionResponse(ok = false, message = "No order was proposed.")

        return when (val moved = boardService.reorderForBuddy(userId, projectId, ids)) {
            0 -> BuddyActionResponse(ok = true, message = "Your board was already in that order.")
            else -> BuddyActionResponse(ok = true, message = "Rearranged — $moved cards moved.")
        }
    }

    // -- Reading the board -------------------------------------------------------------------------

    /** What is on the hire's board, without creating one for somebody who has never opened it. */
    private fun cardsOn(scope: Scope): List<BoardCardResponse>? =
        if (boardService.hasBoard(scope.userId, scope.projectId)) {
            boardService.getBoard(scope.userId, scope.projectId)?.cards
        } else {
            null
        }

    /** The one card of the hire's own of [kind] that the call's `card_id` names. */
    private fun ownCard(call: BuddyToolCallDto, scope: Scope, kind: BoardCardKind): Found<BoardCardResponse> {
        val cardId = call.uuidArg("card_id") ?: return Found.Refused(NO_CARD_ID)
        val card = cardsOn(scope)
            ?.firstOrNull { it.id == cardId }
            ?.takeIf { it.kind == kind && it.owner == BoardCardOwner.HIRE }
            ?: return Found.Refused(
                "That id is not a ${kind.name.lowercase()} on the hire's board. Read read_board again " +
                    "and pass the id it gives for the card you mean.",
            )
        return Found.Ok(card)
    }

    /**
     * The cards the call's `card_ids` name, in the order given, keeping only the ones on the board.
     *
     * Refuses when none are, and says which ids missed when some did — a model told nothing about
     * the one it got wrong will describe the proposal as covering it.
     */
    private fun namedCards(call: BuddyToolCallDto, scope: Scope): Found<List<BoardCardResponse>> {
        val ids = call.uuidListArg("card_ids").distinct().take(MAX_CARDS)
        if (ids.isEmpty()) return Found.Refused(NO_CARD_IDS)

        val byId = cardsOn(scope).orEmpty().associateBy { it.id }
        val cards = ids.mapNotNull { byId[it] }
        return when {
            cards.isEmpty() -> Found.Refused(
                "None of those ids are cards on the hire's board. Read read_board again and pass " +
                    "the ids it gives.",
            )
            cards.size < ids.size -> Found.Refused(
                "${ids.size - cards.size} of those ids are not on the hire's board, so nothing was " +
                    "proposed. Read read_board again and pass only the ids it gives.",
            )
            else -> Found.Ok(cards)
        }
    }

    /** A lookup on the board: what the call named, or the sentence saying why it names nothing. */
    private sealed interface Found<out T> {
        data class Ok<T>(
            val value: T,
        ) : Found<T>

        data class Refused(
            val reason: String,
        ) : Found<Nothing>
    }

    // -- Shared ------------------------------------------------------------------------------------

    private fun offer(type: BuddyActionType, scope: Scope, proposal: BuddyActionProposal): ProposeOutcome =
        ProposeOutcome(toolResult = offeredOnBoard(type, scope.projectName), proposal = proposal)

    private fun refused(reason: String) = ProposeOutcome(reason, null)

    private fun linkRequest(url: String, label: String?) =
        LinkCardRequest(url = url, label = label?.trim()?.take(MAX_LABEL)?.ifBlank { null })

    /**
     * Only web addresses. The hire's own link card takes whatever they type; a link the buddy
     * writes is one the hire will click trusting it, so a `javascript:` or `file:` address never
     * reaches their board this way.
     */
    private fun String.isWebAddress(): Boolean =
        length <= MAX_URL_LENGTH &&
            (startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true))

    private fun BuddyToolCallDto.stringArg(name: String): String =
        (arguments[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun BuddyToolCallDto.stringListArg(name: String): List<String> =
        (arguments[name] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { line -> line.isNotBlank() } }

    private fun BuddyToolCallDto.uuidArg(name: String): UUID? =
        runCatching { UUID.fromString(stringArg(name).trim()) }.getOrNull()

    private fun BuddyToolCallDto.uuidListArg(name: String): List<UUID> =
        stringListArg(name).mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }

    private companion object {
        val HANDLED = setOf(
            BuddyActionType.PLACE_LINK,
            BuddyActionType.EDIT_NOTE,
            BuddyActionType.EDIT_LINK,
            BuddyActionType.EDIT_CHECKLIST,
            BuddyActionType.DISMISS_CARDS,
            BuddyActionType.REORDER_CARDS,
        )

        // The same caps `place_checklist` and `place_note` apply, for the same reasons.
        const val MAX_CHECKLIST_ITEMS = 25
        const val MAX_CHECKLIST_ITEM_LENGTH = 300
        const val MAX_CHECKLIST_TITLE = 120
        const val MAX_NOTE_LENGTH = 2000

        /** A link's name is a heading, not a sentence. */
        const val MAX_LABEL = 120

        /** Longer than any real address, short enough that a card cannot carry a payload in one. */
        const val MAX_URL_LENGTH = 2000

        /** More than a real board holds; a cap so one confirm cannot name an unbounded list. */
        const val MAX_CARDS = 60

        const val NO_CARD_ID = "No card_id was provided. Read read_board to find the card you mean, and pass its id."
        const val NO_CARD_IDS =
            "No card_ids were provided. Read read_board to find the cards you mean, and pass their ids."
        const val NOT_A_WEB_ADDRESS = "That is not a web address. A link needs to start with https:// or http://."

        private fun cardIdParam() = buildJsonObject {
            put("type", "string")
            put("description", "The card's id, exactly as read_board gave it.")
        }

        private fun cardIdsParam(description: String) = buildJsonObject {
            put("type", "array")
            put("description", description)
            putJsonObject("items") { put("type", "string") }
        }

        val PLACE_LINK_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.PLACE_LINK.toolName,
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

        val EDIT_NOTE_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.EDIT_NOTE.toolName,
            description = "Offer to rewrite one of the hire's notes, when THEY ask you to change it " +
                "— 'can you edit this note', 'update my note about the deploy'. Never on your own " +
                "initiative: the note is theirs, and tidying words nobody asked you to tidy is how " +
                "a board stops being somebody's own. Read read_board for the card's id and its " +
                "current text, and pass the WHOLE new text, not only the part that changes — it " +
                "replaces the note. Keep everything they did not ask to change as it was. They see " +
                "the new text on a confirm button; afterwards the card says you changed it.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("card_id", cardIdParam())
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "The note's full new text. Markdown; first line is the heading.")
                    }
                }
                putJsonArray("required") {
                    add("card_id")
                    add("text")
                }
            },
        )

        val EDIT_LINK_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.EDIT_LINK.toolName,
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

        val EDIT_CHECKLIST_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.EDIT_CHECKLIST.toolName,
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

        val DISMISS_CARDS_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.DISMISS_CARDS.toolName,
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

        val REORDER_CARDS_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.REORDER_CARDS.toolName,
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
