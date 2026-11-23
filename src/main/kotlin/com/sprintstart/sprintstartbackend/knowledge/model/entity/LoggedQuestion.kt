package com.sprintstart.sprintstartbackend.knowledge.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One question in the question log, keyed by the chat message it was asked in.
 *
 * [userId] is who asked it, kept so their questions are erased with them.
 */
@Entity
@Table(
    name = "knowledge_questions",
    indexes = [Index(name = "idx_knowledge_questions_project_asked", columnList = "project_id, asked_at")],
)
class LoggedQuestion(
    @Id
    val id: UUID,
    @Column(name = "session_id", nullable = false)
    val sessionId: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(nullable = false, columnDefinition = "TEXT")
    val text: String,
    @Column(name = "asked_at", nullable = false)
    val askedAt: Instant,
)
