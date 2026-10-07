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
import jakarta.persistence.Version
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    /**
     * Which content this card is on: starts at zero and goes up by one with every real content
     * change, in [replacePayload] and nowhere else.
     *
     * The undo token. A client holding the previous version echoes back the revision it saw, and
     * the restore is refused unless that is still the card's. A counter rather than a time because
     * a time is a wall clock, not an identity — two edits can share a millisecond, and a clock can
     * stand still or step back. This one cannot, and it does not move for changes that leave the
     * content alone (dismissing, reordering), which [version] does.
     */
    @Column(name = "content_revision", nullable = false, columnDefinition = "bigint not null default 0")
    var contentRevision: Long = 0,
    /**
     * Optimistic-lock version, bumped by Hibernate on every update of the row.
     *
     * Every write to a card replaces the whole row, so two transactions that read the same state
     * would otherwise each write theirs over the other's — an undo silently discarding an edit that
     * committed in between, along with the only copy of what it replaced. With this, the second
     * writer's UPDATE matches no row and fails instead of committing a stale card. Not the undo
     * token — see [contentRevision].
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    var version: Long = 0,
) {
    /**
     * Replaces this card's content, keeping what it said before as the one previous version.
     *
     * The single way an authored card's content changes after creation, so no edit can skip the
     * snapshot. An edit that leaves the content exactly as it was is not an edit: it records
     * nothing and, more importantly, does not overwrite the previous version — a no-op save must not
     * be what takes away the undo for a real change made just before it.
     *
     * [contentRevision] goes up with it, which is what names this edit to a client that wants to
     * undo exactly it. [previousReplacedAt] is for showing when, not for telling edits apart.
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
        val stamp = atClientPrecision(at)
        previousPayload = payload
        previousReplacedBy = by
        previousReplacedAt = stamp
        contentRevision += 1
        payload = newPayload
        recordChange(change, by, stamp)
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
        val stamp = atClientPrecision(at)
        lastChange = change
        lastChangedBy = by
        lastChangedAt = stamp
        updatedAt = stamp
    }

    companion object {
        /**
         * [at], cut to whole milliseconds.
         *
         * So a time shown to a client comes back from the database and through a JavaScript `Date`
         * unchanged: `Instant.now()` can carry nanoseconds, Postgres keeps microseconds and a `Date`
         * milliseconds. It says when, and nothing more — it is not unique and is not used to
         * identify an edit; [contentRevision] is.
         */
        fun atClientPrecision(at: Instant): Instant = at.truncatedTo(ChronoUnit.MILLIS)
    }
}
