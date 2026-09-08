package com.sprintstart.sprintstartbackend.onboarding.model.entity

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
     * What a [BoardCardKind.DIAGRAM] card is a diagram *of*; null for every other kind.
     *
     * The question, never the answer — which is the whole reason a live card is allowed to store
     * this at all. The picture is re-derived from the corpus on every read, so a diagram cannot
     * describe code that has since moved; only the thing somebody asked about is durable.
     *
     * It is also this card's identity: two subjects are two diagrams, so uniqueness for the kind is
     * per `(board, subject)` rather than per board. Compared case-insensitively, or the same
     * question asked twice with different capitals becomes two cards.
     */
    @Column(columnDefinition = "TEXT")
    var subject: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
