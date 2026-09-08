package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * One user's durable proficiency in one competency — the progress ledger.
 *
 * This is the load-bearing record of the rework: progress lives here on the competency, keyed by
 * the stable [competencyKey], and must survive path regeneration. The onboarding path is a
 * disposable projection that may be rebuilt freely, but reconciliation and personalization must
 * never delete or overwrite these rows as a side effect (the boundary enforced in Phase 0c).
 *
 * [level] is 0..4, aligned 1:1 with the AI `SKILL_LEVELS` (beginner..expert -> 1..4), with 0
 * meaning unknown/not yet placed. A user has at most one entry per competency.
 */
@Entity
@Table(
    name = "user_competency_state",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_user_competency_state_user_competency",
            columnNames = ["user_id", "competency_key"],
        ),
    ],
)
class UserCompetencyState(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "competency_key", nullable = false)
    val competencyKey: String,
    @Column(nullable = false)
    var level: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: CompetencySource,
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
