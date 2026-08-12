package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One graded attempt at a [Verification] — the audit trail behind a pass/fail.
 *
 * ⚠️ Attempts point at a *verification*, never at whatever owns it. That is what has let the
 * structures above them be replaced repeatedly without a single attempt row being repointed or
 * lost, and it is worth preserving.
 */
@Entity
@Table(name = "verification_attempts")
class VerificationAttempt(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "verification_id", nullable = false)
    val verification: Verification,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(nullable = false, columnDefinition = "TEXT")
    val answer: String,
    @Column(nullable = false)
    val passed: Boolean,
    @Column(nullable = false)
    val score: Double,
    @Column(nullable = false, columnDefinition = "TEXT")
    val feedback: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    val hint: String? = null,
    @Column(name = "attempt_no", nullable = false)
    val attemptNo: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
