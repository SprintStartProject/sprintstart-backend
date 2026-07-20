package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A knowledge-check question a user must re-answer in a later phase.
 *
 * When a user passes a phase but got questions wrong (in any attempt), those
 * questions are carried over so they are re-tested at the end of the next phase's
 * check — verifying the content was actually understood, not just eventually
 * guessed. The references are stored as plain UUIDs (not JPA associations) so a
 * carried question survives edits to the original check and stays decoupled from
 * the source phase.
 *
 * [targetPhaseId] is the phase the question is currently re-asked in; it advances
 * to the following phase each time the question is answered incorrectly. [resolved]
 * becomes true once the question is finally answered correctly (terminal).
 */
@Entity
@Table(name = "phase_check_review_items")
class PhaseCheckReviewItem(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val userId: UUID,
    @Column(nullable = false)
    val questionId: UUID,
    @Column(nullable = false)
    val sourcePhaseId: UUID,
    @Column(nullable = false)
    var targetPhaseId: UUID,
    @Column(nullable = false)
    var resolved: Boolean = false,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
