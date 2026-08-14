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
 * A group is one recurring question — the same thing asked in different words — and belongs to a
 * [category], the topic bucket PMs browse by. Groups are maintained incrementally: a question asked
 * in chat either joins an existing group or opens a new one, so the rows are living state rather
 * than a pure cache. A full refresh still replaces the whole set as a fallback.
 *
 * [occurrenceCount] is the authoritative number of times the group's question was asked. It can
 * exceed the number of stored [questions] after a full refresh, which only carries back a redacted
 * sample; questions arriving through the live path are each stored.
 */
@Entity
class FaqGroup(
    @Id
    val id: UUID = UUID.randomUUID(),
    // See KnowledgeGap.projectId — same lifecycle, same nullability reason.
    @Column(name = "project_id")
    val projectId: UUID? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var question: String,
    @Column(nullable = false)
    var occurrenceCount: Int,
    // Nullable rather than defaulted: rows written before categories existed genuinely have no
    // category, and labelling them "Uncategorized" would claim the classifier looked at them and
    // gave up. A consolidation pass can file them later.
    @Column(name = "category")
    var category: String? = null,
    @Column(name = "first_asked_at", nullable = false)
    var firstAskedAt: Instant = Instant.now(),
    // Distinct from refreshedAt: when the group's question was last actually asked, which is what
    // drives recency and the fading of stale topics. refreshedAt only records when this row was
    // last written.
    @Column(name = "last_asked_at", nullable = false)
    var lastAskedAt: Instant = Instant.now(),
    @Column(nullable = false)
    var refreshedAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    val questions: MutableList<FaqQuestion> = mutableListOf(),
    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    val documents: MutableList<FaqDocument> = mutableListOf(),
)
