package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A knowledge-check question a user still has to prove they understood.
 *
 * When a user passes a phase but got questions wrong (in any attempt), those
 * questions are collected here so they can be re-tested later — verifying the
 * content was actually understood, not just eventually guessed. The references are
 * stored as plain UUIDs (not JPA associations) so a collected question survives
 * edits to the original check and stays decoupled from the source phase.
 *
 * Open items form a single, phase-independent review pool per user: they are
 * answered in the standalone review check rather than being appended to the next
 * phase's check, because a question from an earlier phase is thematically out of
 * place there. The pool also gates onboarding completion — a user counts as
 * onboarded only once the final phase check passed *and* no open items remain.
 *
 * [resolved] becomes true once the question is finally answered correctly (terminal).
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
    /**
     * Retained only for schema compatibility; carries no meaning since the review pool
     * became phase-independent.
     *
     * The column is `NOT NULL` in existing databases and the schema is maintained by
     * Hibernate's `ddl-auto: update`, which neither drops columns nor relaxes nullability.
     * Dropping the field would therefore keep working on a freshly created schema while
     * breaking inserts against every existing one, so it stays and is written as a copy of
     * [sourcePhaseId]. Remove it together with an explicit schema migration.
     */
    @Column(nullable = false)
    var targetPhaseId: UUID,
    @Column(nullable = false)
    var resolved: Boolean = false,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
