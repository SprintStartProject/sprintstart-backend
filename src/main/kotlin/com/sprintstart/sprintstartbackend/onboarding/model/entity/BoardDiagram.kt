package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * The last picture assembled for one [BoardCard] of kind `DIAGRAM` — a cache, and nothing more.
 *
 * A diagram costs an LLM call to derive and a board card hydrates on *every* page load, so the
 * picture is cached — and the cache is validated, never trusted: every revalidation sends
 * [corpusFingerprint], an unchanged corpus comes back `unchanged` with no retrieval and no
 * generation, and a corpus that has moved is redrawn.
 *
 * The *question* is not here: it lives on [BoardCard.subject], so it survives the cache being
 * dropped.
 *
 * A diagram that will not decode is a cache miss, not an error — everything in it is
 * derivable and none of it was anybody's work. (A [BoardCardPayload] that will not decode fails the
 * board read instead, because that *is* the hire's own work.)
 */
@Entity
@Table(name = "board_diagrams")
class BoardDiagram(
    /** The card this is the picture for. One card, one cached diagram. */
    @Id
    @Column(name = "card_id")
    val cardId: UUID,
    /**
     * The corpus this picture was drawn from.
     *
     * The whole cache-validation mechanism: sent on every revalidation so the AI service can answer
     * "nothing changed" without doing any work, and compared against the *current* corpus rather
     * than against a clock. Age is not staleness — a diagram of code nobody has touched in a year is
     * perfectly current.
     */
    @Column(name = "corpus_fingerprint")
    var corpusFingerprint: String? = null,
    @Column(name = "model")
    var model: String? = null,
    /** The assembled nodes, edges and sources as JSON. See the class note for why it is not rows. */
    @Column(columnDefinition = "TEXT", nullable = false)
    var payload: String,
    @Column(name = "assembled_at", nullable = false)
    var assembledAt: Instant = Instant.now(),
)
