package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.AttestationState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A named person confirming that a hire did a piece of real work, and that it met the bar.
 *
 * A table rather than a derivation because it is the one piece of evidence that exists nowhere
 * else: nobody records "the retro was well run" until somebody is asked.
 *
 * [attesterId] must be a different person from [hireId], enforced at the service boundary.
 * Letting a hire sign off their own work here would put the weakest possible evidence under the
 * metric onboarding is judged on while calling it something stronger.
 *
 * Rework is counted, not smoothed over. A returned attestation goes back to
 * [AttestationState.REQUESTED] and increments [returnedCount], exactly as a pull request sent back
 * for changes does — an attestation that took three passes must not read like one that took none.
 */
@Entity
@Table(name = "attestations")
class Attestation(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "hire_id", nullable = false)
    val hireId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    /** What the hire says they did, in their words. Shown to the attester, never graded here. */
    @Column(nullable = false, columnDefinition = "TEXT")
    val title: String,
    /** Where the work can be seen, when it lives somewhere linkable. Optional by nature. */
    @Column(name = "evidence_url", nullable = true)
    val evidenceUrl: String? = null,
    /** The person asked to confirm it. Never [hireId]; see the class note. */
    @Column(name = "attester_id", nullable = false)
    val attesterId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: AttestationState = AttestationState.REQUESTED,
    @Column(name = "requested_at", nullable = false)
    val requestedAt: Instant = Instant.now(),
    /**
     * When the attester first responded either way.
     *
     * The same measurement as a pull request's first response, and the same finding when it is
     * null: nobody has answered yet. Set once and never moved, so a second pass does not erase how
     * long the first one took.
     */
    @Column(name = "first_response_at", nullable = true)
    var firstResponseAt: Instant? = null,
    /** When it was accepted. Null until then; this is the contribution's acceptance moment. */
    @Column(name = "accepted_at", nullable = true)
    var acceptedAt: Instant? = null,
    @Column(name = "returned_count", nullable = false)
    var returnedCount: Int = 0,
    /** Why it was sent back, for the hire to act on. Cleared when they resubmit. */
    @Column(name = "return_reason", nullable = true, columnDefinition = "TEXT")
    var returnReason: String? = null,
)
