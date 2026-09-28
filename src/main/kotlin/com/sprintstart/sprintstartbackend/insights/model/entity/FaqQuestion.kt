package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.Instant
import java.util.UUID

/**
 * A single asked question that belongs to a [FaqGroup].
 *
 * There is one row per *ask*, not per distinct phrasing — that is what makes a group's trend and
 * recent count exact, since a recurring question is exactly one that gets repeated.
 *
 * [text] is empty for asks whose wording did not come back. A rebuild samples by distinct text and
 * caps how many it returns, so most of what it regroups arrives as a count without a phrasing; the
 * row still has to exist to be counted. Readers showing questions must skip the blank ones.
 *
 * Non-empty text is stored as delivered by the AI service, which is responsible for stripping
 * personally identifiable information before the questions reach this module.
 */
@Entity
class FaqQuestion(
    @Id
    val id: UUID = UUID.randomUUID(),
    // Empty rather than null, so an existing database needs no constraint change — Hibernate's
    // schema update adds columns but never relaxes a NOT NULL, and a nullable field here would
    // fail on insert against a table created before it.
    @Column(nullable = false, columnDefinition = "TEXT")
    val text: String,
    @Column(name = "asked_at", nullable = false)
    val askedAt: Instant = Instant.now(),
    // The chat message this question came from. Nullable because rows written before the live path
    // existed have no origin on record. It is what makes classification idempotent: a redelivered
    // event must not count the same message twice.
    @Column(name = "source_message_id")
    val sourceMessageId: UUID? = null,
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    var group: FaqGroup,
)
