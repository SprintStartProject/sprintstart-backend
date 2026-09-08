package com.sprintstart.sprintstartbackend.onboarding.model.request.board

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import java.util.UUID

/**
 * What the hire wants to put on their board, or what they want a card of theirs to say now.
 *
 * Polymorphic on `kind` rather than one request with every field nullable, so a note cannot arrive
 * carrying a URL and a checklist cannot arrive without its items. The same shape serves create and
 * edit: an edit replaces the card's content outright, because these are small and are read and
 * written whole — a patch language for a three-line note would be more machinery than the note.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "kind",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = NoteCardRequest::class, name = "NOTE"),
    JsonSubTypes.Type(value = LinkCardRequest::class, name = "LINK"),
    JsonSubTypes.Type(value = ChecklistCardRequest::class, name = "CHECKLIST"),
)
sealed interface AuthoredCardRequest {
    val kind: BoardCardKind
}

data class NoteCardRequest(
    override val kind: BoardCardKind = BoardCardKind.NOTE,
    val text: String,
) : AuthoredCardRequest

data class LinkCardRequest(
    override val kind: BoardCardKind = BoardCardKind.LINK,
    val url: String,
    /** Optional: an absent label shows the URL, which is worse to read but always true. */
    val label: String? = null,
) : AuthoredCardRequest

data class ChecklistCardRequest(
    override val kind: BoardCardKind = BoardCardKind.CHECKLIST,
    val title: String? = null,
    val items: List<ChecklistItemRequest> = emptyList(),
) : AuthoredCardRequest

/**
 * One checklist item.
 *
 * [id] is null for an item the hire has just typed and carries the existing id for one already on
 * the card. That is what makes ticking a line an edit to *that* line rather than to a position:
 * without it, adding a line above a ticked item would silently move the tick.
 */
data class ChecklistItemRequest(
    val id: UUID? = null,
    val text: String,
    val done: Boolean = false,
)

/**
 * The hire's cards in the order they want them.
 *
 * One ordered list rather than a from/to pair, because a drag is a statement about the whole board
 * and reconstructing that from a single move is how two clients end up disagreeing about the order.
 * Cards left out keep their place after the listed ones.
 */
data class ReorderBoardRequest(
    val cardIds: List<UUID>,
)
