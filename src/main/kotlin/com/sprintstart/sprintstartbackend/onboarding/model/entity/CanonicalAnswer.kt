package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A durable answer a human gave to a question the buddy could not, promoted into knowledge.
 *
 * This is what makes the AI mentor *grow*: when a hire escalates a gap and a PM answers it, the
 * answer is kept here as the source of truth — editable when reality changes — and served back to
 * the next hire who asks something like it. One answer can resolve many
 * [KnowledgeRequest]s (the same gap gets hit repeatedly); the request is the event, this is the
 * knowledge.
 *
 * Per project, because an answer is only true inside the project whose docs and conventions it
 * describes — the competency it teaches may be global, but "how we deploy" is not.
 */
@Entity
@Table(name = "canonical_answers")
class CanonicalAnswer(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    // The question this answers, in the words a future hire might search — seeded from the request
    // but a PM may generalise it so it matches more than the one phrasing that triggered it.
    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    var question: String,
    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    var answer: String,
    // The PM who authored (or last edited) the answer — provenance a hire can see and trust.
    @Column(name = "author_id", nullable = false)
    var authorId: UUID,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
