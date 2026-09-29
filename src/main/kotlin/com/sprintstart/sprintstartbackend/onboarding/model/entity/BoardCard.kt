package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One card on a [Board].
 *
 * For a live card the row holds no content — only that this hire wants this card, where it
 * sits, and whether it is still there. The content is re-read on every board load from the same
 * services the buddy's tools read, so a card and the tool of the same name cannot disagree.
 * Authored cards (a note, a link, a checklist) do have content of their own, in [payload].
 *
 * One row per kind, except for the ones the hire writes — which is also what makes "ensure
 * this card exists" idempotent. The database enforces it with a partial unique index covering only
 * the non-authored kinds; Hibernate cannot express a partial index, so the constraint is absent
 * from this mapping and [BoardService] enforces the same rule in code.
 */
@Entity
@Table(name = "board_cards")
class BoardCard(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "board_id", nullable = false)
    val boardId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val kind: BoardCardKind,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val owner: BoardCardOwner,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: BoardCardState = BoardCardState.ACTIVE,
    /**
     * Where the card sits in the board's order, ascending.
     *
     * An integer rather than an x/y pair: the board is a responsive grid a hire can reorder, not a
     * canvas they position things on. A free canvas was considered and deferred — it would not
     * survive a phone screen, and reordering is the part that carries the meaning.
     */
    @Column(nullable = false)
    var position: Int,
    /**
     * When the mentor put this card here, or null when the board keeps it as part of the baseline.
     *
     * Not a redundant twin of [owner], which answers who may *change* a card. This answers where it
     * came from, and it is user-visible: "the board keeps this for you" and "your buddy put this
     * here on Tuesday" are different claims, and only one of them is true of a card nobody chose.
     * Saying the stronger one about a card the board seeded would be the board's first lie.
     */
    @Column(name = "placed_at")
    var placedAt: Instant? = null,
    /**
     * The content of a card the hire wrote, as JSON; null for every live card.
     *
     * Stored as text and decoded on read rather than mapped into columns, because these are small,
     * are read and written whole, and nothing ever queries inside them.
     */
    @Column(columnDefinition = "TEXT")
    var payload: String? = null,
    /**
     * What a card whose kind takes one is *of*; null for every other kind
     * ([BoardCardKind.takesSubject]).
     *
     * For [BoardCardKind.DIAGRAM] it is the question, never the answer — the picture is re-derived
     * from the corpus on every read, so a diagram cannot describe code that has since moved; only the
     * thing somebody asked about is durable. For [BoardCardKind.PATH_STEP] it is the resolved step's
     * id, stored rather than the title the mentor said, so a renamed step keeps its card.
     *
     * It is also this card's identity: two subjects are two cards of the same kind, so uniqueness for
     * the kind is per `(board, subject)` rather than per board. Compared case-insensitively, or the
     * same question asked twice with different capitals becomes two cards.
     */
    @Column(columnDefinition = "TEXT")
    var subject: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    /**
     * The most recent change anybody made to this card, or null for one nobody has touched since
     * the board seeded it.
     *
     * [placedAt] set the precedent — attribution the hire cannot check is attribution they cannot
     * trust — and this extends it past creation. Once the buddy can edit, tick, dismiss and move a
     * hire's cards, "your buddy put this here" is no longer the only claim worth making, and a note
     * whose words changed must be able to say who changed them. Set together with [lastChangedBy] and
     * [lastChangedAt] through [recordChange], never one at a time.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_change")
    var lastChange: BoardCardChange? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "last_changed_by")
    var lastChangedBy: BoardActor? = null,
    @Column(name = "last_changed_at")
    var lastChangedAt: Instant? = null,
    /**
     * What this card said before its most recent content edit, whole — or null when it has never
     * been edited. Authored cards only.
     *
     * The undo for an edit. While only the hire could edit, replacing content outright was fine:
     * the only person who could lose their words was the one choosing to. Once the buddy can rewrite
     * a hire's note, an unrecoverable overwrite is a real way to lose somebody's work.
     *
     * **A depth of one, on the row, on purpose.** Undo needs the content an edit replaced and
     * nothing older; anything deeper is a version history nobody has asked for. Keeping it here
     * rather than in a table of snapshots is what bounds it: the next edit supersedes this one, so
     * a card holds at most one previous version however often it changes. Whole content rather than
     * a diff, for the reason edits are whole — a patch language for a three-line note would be more
     * machinery than the note.
     *
     * Written only through [replacePayload], together with [previousReplacedBy] and
     * [previousReplacedAt], which say whose edit and when this content was replaced — hire and
     * buddy alike, so the history reads the same whoever made it.
     */
    @Column(name = "previous_payload", columnDefinition = "TEXT")
    var previousPayload: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_replaced_by")
    var previousReplacedBy: BoardActor? = null,
    @Column(name = "previous_replaced_at")
    var previousReplacedAt: Instant? = null,
) {
    /**
     * Replaces this card's content, keeping what it said before as the one previous version.
     *
     * The single way an authored card's content changes after creation, so no edit can skip the
     * snapshot. An edit that leaves the content exactly as it was is not an edit: it records
     * nothing and, more importantly, does not overwrite the previous version — a no-op save must not
     * be what takes away the undo for a real change made just before it.
     *
     * @return Whether anything changed.
     */
    fun replacePayload(
        newPayload: String,
        change: BoardCardChange,
        by: BoardActor,
        at: Instant = Instant.now(),
    ): Boolean {
        if (newPayload == payload) return false
        previousPayload = payload
        previousReplacedBy = by
        previousReplacedAt = at
        payload = newPayload
        recordChange(change, by, at)
        return true
    }

    /**
     * Notes who just changed this card and how, and bumps [updatedAt] with it.
     *
     * The one way the attribution columns are written, so the three can never disagree — a change
     * with no author, or an author with no time, is exactly the unverifiable claim they exist to
     * replace.
     */
    fun recordChange(change: BoardCardChange, by: BoardActor, at: Instant = Instant.now()) {
        lastChange = change
        lastChangedBy = by
        lastChangedAt = at
        updatedAt = at
    }
}
