package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A hire hit a wall the buddy could not answer, and chose to send the question to a person.
 *
 * The event half of the growth loop: the queue a PM works through. Deliberately hire-chosen, not
 * automatic — the buddy offers to escalate and the hire decides, so a PM's inbox is questions
 * somebody actually wanted answered, not every failed retrieval. Once answered it points at the
 * [CanonicalAnswer] that resolved it, so the same durable answer can close many requests.
 */
@Entity
@Table(name = "knowledge_requests")
class KnowledgeRequest(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(name = "hire_id", nullable = false)
    val hireId: UUID,
    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    val question: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: KnowledgeRequestStatus = KnowledgeRequestStatus.OPEN,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "answered_by")
    var answeredBy: UUID? = null,
    @Column(name = "answered_at")
    var answeredAt: Instant? = null,
    @Column(name = "canonical_answer_id")
    var canonicalAnswerId: UUID? = null,
)
