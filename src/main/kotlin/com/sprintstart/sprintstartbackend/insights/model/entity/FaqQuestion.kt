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
 * Questions arriving through the live path are each stored, which is what makes a group's trend
 * exact; a full refresh only carries back a redacted sample, so after one a group holds fewer rows
 * than its occurrence count. Either way the detail view shows a capped sample, not every row.
 *
 * The text is stored as delivered by the AI service, which is responsible for stripping personally
 * identifiable information before the questions reach this module.
 */
@Entity
class FaqQuestion(
    @Id
    val id: UUID = UUID.randomUUID(),
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
