package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.HireActionContext
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.board.external.HireBoardApi
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The fewest lines that count as a list worth keeping.
 *
 * Two, matching `checklistFromMarkdown` on the client, and for the same reason: one bullet is how a
 * model emphasises a sentence, and a card made of it repeats the reply above it.
 */
internal const val MIN_CHECKLIST_ITEMS = 2

/** How many lines a card will take. Past this it is a document, not a checklist. */
internal const val MAX_CHECKLIST_ITEMS = 25

/** A step is a line, not a paragraph — anything longer is cut rather than refused. */
internal const val MAX_CHECKLIST_ITEM_LENGTH = 300

/** A heading length: long enough to say what the list is, short enough not to wrap. */
internal const val MAX_CHECKLIST_TITLE = 120

/**
 * The fewest characters worth a note card of its own.
 *
 * Every reply already carries a button that keeps the whole answer, so a card holding one short
 * sentence out of it is worth less than the thing the hire could have pressed anyway.
 */
internal const val MIN_NOTE_LENGTH = 80

/** Past this a note is a document. `NoteCard` already folds a long one behind a count. */
internal const val MAX_NOTE_LENGTH = 2000

/** A link's name is a heading, not a sentence. */
internal const val MAX_LABEL = 120

/** Longer than any real address, short enough that a card cannot carry a payload in one. */
internal const val MAX_URL_LENGTH = 2000

/** More than a real board holds; a cap so one confirm cannot name an unbounded list. */
internal const val MAX_CARDS = 60

internal const val NO_CHECKLIST_ID =
    "No card_id was provided. Read read_board to find the checklist you mean, and pass its id."
internal const val NO_CARD_ID = "No card_id was provided. Read read_board to find the card you mean, and pass its id."
internal const val NO_CARD_IDS =
    "No card_ids were provided. Read read_board to find the cards you mean, and pass their ids."
internal const val NOT_A_WEB_ADDRESS = "That is not a web address. A link needs to start with https:// or http://."

/** What a confirmed edit of a whole card says: it happened, and the card shows whose change it was. */
internal val EDITED = BuddyActionResponse(
    ok = true,
    message = "Updated. The card shows it was your buddy's change, and it's still yours to edit.",
)

/** The board a project-scoped action's hire has on the project the Buddy resolved. */
internal val HireActionContext.board: HireBoard get() = HireBoard(hire, requireProject.id)

/**
 * What the model is told after offering any board write: offered, not done.
 *
 * Shared by every board action so each proposal makes the same promise in the same words. The mentor
 * never learns what became of it, and a confirmed proposal leaves the screen, so pointing at the button
 * afterwards is how a hire ends up being told they must be missing something that is not there.
 */
internal fun offeredOnBoard(label: String, projectName: String): String =
    "Proposed to the hire on $projectName: “$label”. They will see a " +
        "confirm button showing what would change; it runs only if they click. Offer it " +
        "— do not claim it is done. You will never be told whether they confirmed it, and a " +
        "confirmed proposal leaves the screen, so do not describe the button or ask them to " +
        "click it again. If they say nothing happened, believe them and call the tool afresh."

/** [proposal] as an offer on the hire's board in [context]. */
internal fun offer(context: HireActionContext, proposal: HireActionProposal): HireActionOutcome =
    HireActionOutcome(toolResult = offeredOnBoard(proposal.label, context.requireProject.name), proposal = proposal)

internal fun refused(reason: String) = HireActionOutcome(reason, null)

/** Checklist lines as they may be written: trimmed, cut to a line, blanks dropped, capped in number. */
internal fun cappedLines(items: List<String>?): List<String> =
    items
        .orEmpty()
        .map { it.trim().take(MAX_CHECKLIST_ITEM_LENGTH) }
        .filter { it.isNotBlank() }
        .take(MAX_CHECKLIST_ITEMS)

internal fun linkRequest(url: String, label: String?) =
    LinkCardRequest(url = url, label = label?.trim()?.take(MAX_LABEL)?.ifBlank { null })

/**
 * Only web addresses. The hire's own link card takes whatever they type; a link the buddy writes is
 * one the hire will click trusting it, so a `javascript:` or `file:` address never reaches their board
 * this way.
 */
internal fun String.isWebAddress(): Boolean =
    length <= MAX_URL_LENGTH &&
        (startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true))

internal fun cardIdParam(): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", "The card's id, exactly as read_board gave it.")
}

internal fun cardIdsParam(description: String): JsonObject = buildJsonObject {
    put("type", "array")
    put("description", description)
    putJsonObject("items") { put("type", "string") }
}

/** A lookup on the board: what the call named, or the sentence saying why it names nothing. */
internal sealed interface Found<out T> {
    data class Ok<T>(
        val value: T,
    ) : Found<T>

    data class Refused(
        val reason: String,
    ) : Found<Nothing>
}

/** What is on the hire's [board], without creating one for somebody who has never opened it. */
internal fun HireBoardApi.cardsOn(board: HireBoard): List<BoardCardResponse>? =
    if (hasBoard(board)) boardOf(board)?.cards else null

/**
 * The one card of the hire's own of [kind] that the call's `card_id` names.
 *
 * **Names are resolved here, never taken from the model.** An edit the model described as one card but
 * aimed at another id would be confirmed by a hire reading one thing and agreeing to another, so the
 * card is looked up on the board and the name on the confirm is the board's own.
 */
internal fun HireBoardApi.ownCard(
    call: BuddyToolCallDto,
    board: HireBoard,
    kind: BoardCardKind,
): Found<BoardCardResponse> {
    val cardId = call.uuidArgument("card_id") ?: return Found.Refused(NO_CARD_ID)
    val card = cardsOn(board)
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
 * Refuses when none are, and says which ids missed when some did — a model told nothing about the one
 * it got wrong will describe the proposal as covering it.
 */
internal fun HireBoardApi.namedCards(call: BuddyToolCallDto, board: HireBoard): Found<List<BoardCardResponse>> {
    // The raw array, not a cleaned-up list: dropping a blank, a non-string or a malformed id, or
    // cutting the list at a cap, would propose a change to fewer cards than the model meant, and
    // the model would describe the proposal as covering all of them.
    val named = call.arguments["card_ids"] as? JsonArray
    if (named == null || named.isEmpty()) return Found.Refused(NO_CARD_IDS)
    if (named.size > MAX_CARDS) {
        return Found.Refused(
            "${named.size} ids is more than one proposal can cover (at most $MAX_CARDS), so nothing " +
                "was proposed. Propose fewer cards at a time.",
        )
    }
    val parsed = named.map { it.asCardId() }
    if (parsed.any { it == null }) {
        return Found.Refused(
            "${parsed.count { it == null }} of those ids are not card ids, so nothing was proposed. " +
                "Read read_board again and pass only the ids it gives.",
        )
    }
    val ids = parsed.filterNotNull().distinct()

    val byId = cardsOn(board).orEmpty().associateBy { it.id }
    return onBoard(ids, ids.mapNotNull { byId[it] })
}
