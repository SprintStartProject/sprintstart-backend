package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCardPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistItemPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.LinkPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.NotePayload
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardPreviousResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistItemResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

// What a note, link or checklist stores, and the rules for reading and writing it.
//
// Pure functions over the payload and nothing else — no repository, no membership — pulled out of
// [BoardService] so that the service holds the decisions about *who* may change a card and this
// file holds what a change to one *is*. The hire's own edits and the buddy's go through the same
// functions here, which is what keeps "the buddy may do what the hire can" from growing a second
// set of rules about what a valid card looks like.

/** Lenient on unknown keys so a payload written by a newer version still reads back. */
internal val boardPayloadJson = Json { ignoreUnknownKeys = true }

/**
 * This list with each line that already exists matched back to its id and tick, by its words.
 *
 * Trimmed and case-insensitive, the same as every other buddy checklist edit matches, and each
 * existing line is claimed at most once, so a list that repeats a line cannot hand one tick to
 * both copies.
 */
internal fun ChecklistCardRequest.keepingLinesOf(existing: ChecklistPayload): ChecklistCardRequest {
    val unclaimed = existing.items.toMutableList()
    return copy(
        items = items.map { item ->
            val match = unclaimed.firstOrNull { it.text.trim().equals(item.text.trim(), ignoreCase = true) }
            if (match == null) {
                item.copy(id = null, done = false)
            } else {
                unclaimed.remove(match)
                item.copy(id = UUID.fromString(match.id), done = match.done)
            }
        },
    )
}

/**
 * What the hire wrote, decoded.
 *
 * A payload that cannot be decoded fails the whole board read; it is not swallowed into an
 * empty card. A blank note reads as the board having lost the hire's work.
 */
internal fun authoredContent(payload: String?): BoardCardContent =
    when (val decoded = payload?.let { boardPayloadJson.decodeFromString<BoardCardPayload>(it) }) {
        is NotePayload -> NoteContent(text = decoded.text)
        is LinkPayload -> LinkContent(url = decoded.url, label = decoded.label)
        is ChecklistPayload -> ChecklistContent(
            title = decoded.title,
            items = decoded.items.map {
                ChecklistItemResponse(
                    id = UUID.fromString(it.id),
                    text = it.text,
                    done = it.done,
                )
            },
        )
        // An authored card with no payload cannot happen: one is written when the card is
        // created and replaced when it is edited, never cleared.
        null -> error("Authored board card has no payload")
    }

/**
 * What an edit that went from [before] to [after] was: [BoardCardChange.TICKED] when only a
 * checklist's ticks moved, [BoardCardChange.EDITED] otherwise.
 *
 * Worth telling apart because the hire's checkbox and the hire's rewrite both arrive as a whole
 * payload, and "ticked" and "rewrote" are different things to be told about a card.
 */
internal fun changeBetween(before: String?, after: String?): BoardCardChange {
    val old = before?.let { boardPayloadJson.decodeFromString<BoardCardPayload>(it) } as? ChecklistPayload
    val new = after?.let { boardPayloadJson.decodeFromString<BoardCardPayload>(it) } as? ChecklistPayload
    val onlyTicks = old != null &&
        new != null &&
        old.title == new.title &&
        old.items.map { it.id to it.text } == new.items.map { it.id to it.text }
    return if (onlyTicks) BoardCardChange.TICKED else BoardCardChange.EDITED
}

/**
 * The checklist a card holds.
 *
 * Its own function only because `BoardService.appendChecklistItems` may raise at most two kinds of refusal
 * before detekt calls it a function that does too much deciding — which is a fair thing to be
 * told about a write, so this is the decision that moved rather than the rule that bent.
 *
 * A null payload and a payload of another shape get the same refusal, because they are the
 * same thing from here: a row whose kind says CHECKLIST over content that is not one. Neither
 * is reachable by any write in `BoardService`, which is exactly why it is worth saying out loud
 * rather than asserting.
 */
internal fun BoardCard.checklistOrThrow(): ChecklistPayload =
    payload?.let { boardPayloadJson.decodeFromString<BoardCardPayload>(it) } as? ChecklistPayload
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That card holds no checklist")

/** The card's stored content, or null for a row without any. */
internal fun BoardCard.decodedPayload(): BoardCardPayload? =
    payload?.let { boardPayloadJson.decodeFromString<BoardCardPayload>(it) }

/** How long the note on this card is, in characters; 0 for a card that holds no note. */
internal fun BoardCard.noteLength(): Int = (decodedPayload() as? NotePayload)?.text?.length ?: 0

/**
 * The request as something storable, rejecting content that would leave a card saying nothing.
 *
 * An empty note is not a note and a link with no address is not a link; keeping either would
 * leave a blank card on the board that nobody can explain later. A checklist with no items is
 * allowed — that is a list somebody is about to fill in, which is a real thing to make.
 */
internal fun AuthoredCardRequest.toPayload(): BoardCardPayload = when (this) {
    is NoteCardRequest -> NotePayload(text = text.requireContent("A note needs some text"))
    is LinkCardRequest -> LinkPayload(
        url = url.requireContent("A link needs an address"),
        label = label?.trim()?.ifBlank { null },
    )
    is ChecklistCardRequest -> ChecklistPayload(
        title = title?.trim()?.ifBlank { null },
        items = items
            // A blank line the hire never filled in is not an item; dropping it beats keeping a
            // tickable nothing.
            .filter { it.text.isNotBlank() }
            .map {
                ChecklistItemPayload(
                    // A new item gets its id here rather than from the client, so two tabs
                    // adding a line cannot mint the same one.
                    id = (it.id ?: UUID.randomUUID()).toString(),
                    text = it.text.trim(),
                    done = it.done,
                )
            },
    )
}

private fun String.requireContent(message: String): String =
    trim().ifBlank { throw ResponseStatusException(HttpStatus.BAD_REQUEST, message) }

/** The previous version, decoded — all three snapshot columns, or nothing. */
internal fun BoardCard.toPreviousResponse(): BoardCardPreviousResponse? {
    val content = previousPayload ?: return null
    val by = previousReplacedBy ?: return null
    val at = previousReplacedAt ?: return null
    return BoardCardPreviousResponse(
        content = authoredContent(content),
        replacedBy = by,
        replacedAt = at,
        revision = contentRevision,
    )
}
