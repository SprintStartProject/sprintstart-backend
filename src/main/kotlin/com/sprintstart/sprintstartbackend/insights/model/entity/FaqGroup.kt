package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.Instant
import java.util.UUID

/**
 * A cluster of semantically similar recurring questions surfaced for project managers.
 *
 * A group is one recurring question — the same thing asked in different words — headlined by a
 * short generated [title] rather than by one of its questions verbatim, so a PM scans a phrase
 * instead of reading a sentence per row. Groups are maintained incrementally: a question asked in
 * chat either joins an existing group or opens a new one, so the rows are living state rather than
 * a pure cache. A full refresh still replaces the whole set as a fallback.
 *
 * [occurrenceCount] is the authoritative number of times the group's question was asked. It can
 * exceed the number of stored [questions] after a full refresh, which only carries back a redacted
 * sample; questions arriving through the live path are each stored.
 */
@Entity
class FaqGroup(
    @Id
    val id: UUID = UUID.randomUUID(),
    /** The project this group belongs to; null only for rows from before insights were project-scoped. */
    @Column(name = "project_id")
    val projectId: UUID? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var question: String,
    @Column(nullable = false)
    var occurrenceCount: Int,
    /** A short title; null for older rows, where readers fall back to [question]. */
    @Column(name = "title", columnDefinition = "TEXT")
    var title: String? = null,
    @Column(name = "first_asked_at", nullable = false)
    var firstAskedAt: Instant = Instant.now(),
    /**
     * When the group's question was last asked, which drives recency; [refreshedAt] only records when the row
     * was written.
     */
    @Column(name = "last_asked_at", nullable = false)
    var lastAskedAt: Instant = Instant.now(),
    @Column(nullable = false)
    var refreshedAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    val questions: MutableList<FaqQuestion> = mutableListOf(),
    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    val documents: MutableList<FaqDocument> = mutableListOf(),
)
