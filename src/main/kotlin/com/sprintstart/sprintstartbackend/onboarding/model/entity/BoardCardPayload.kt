package com.sprintstart.sprintstartbackend.onboarding.model.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The stored content of a card the hire wrote.
 *
 * Only authored cards have one. A live card's row holds nothing but its own existence, because its
 * content is re-read from services that already hold those facts durably — a stored copy would be
 * the one that goes stale. An authored card has nowhere else for its content to live, which is the
 * entire reason this exists.
 *
 * Persisted as JSON in one column rather than as a table per kind: these are small, they are read
 * and written whole, and nothing ever queries inside them. A `board_notes` table would be three
 * joins bought with nothing.
 */
@Serializable
sealed interface BoardCardPayload

/** Something the hire wrote down, in markdown. Rendered as theirs, never quoted back as fact. */
@Serializable
@SerialName("NOTE")
data class NotePayload(
    val text: String,
) : BoardCardPayload

/**
 * A link the hire wants to keep.
 *
 * [label] is optional because most links do not need one — when it is absent the client shows the
 * URL, which is worse to read but always true, rather than inventing a title from the address.
 */
@Serializable
@SerialName("LINK")
data class LinkPayload(
    val url: String,
    val label: String? = null,
) : BoardCardPayload

/**
 * A list the hire ticks off — the only card whose content changes by being used.
 *
 * Items carry their own [ChecklistItemPayload.id] so that ticking one is an edit to *that* item
 * rather than to a position: the alternative is that adding a line above a ticked item silently
 * moves the tick.
 */
@Serializable
@SerialName("CHECKLIST")
data class ChecklistPayload(
    val title: String? = null,
    val items: List<ChecklistItemPayload> = emptyList(),
) : BoardCardPayload

@Serializable
data class ChecklistItemPayload(
    val id: String,
    val text: String,
    val done: Boolean = false,
)
