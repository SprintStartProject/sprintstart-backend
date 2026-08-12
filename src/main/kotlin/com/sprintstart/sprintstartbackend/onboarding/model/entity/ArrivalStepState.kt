package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * That one hire has settled one [ArrivalStep], and how it was established.
 *
 * ⚠️ **The row's existence is the state.** There is no status column — the only value it could
 * hold is `SETTLED`. Absence means "not settled yet", never an error, and a hire with no rows at
 * all is one who has just arrived.
 *
 * ⚠️ **[stepKey] is a string, not a foreign key to [ArrivalStep.id].** A definition can be deleted,
 * reworded or re-added and what somebody already did survives it. A cascade would make deleting a
 * step from an authoring screen quietly destroy other people's records.
 *
 * ⚠️ **Monotonic.** Settled never becomes unsettled: a derivation that later cannot see its
 * evidence is not evidence of anything. Writes are idempotent — re-settling leaves the original
 * [settledAt] alone, because the day something happened does not move.
 *
 * ⚠️ **Rigor lives here, not on the definition.** [ArrivalStep.settledBy] says how a step is
 * *meant* to be settled; [rigor] records how it actually was for this hire. They can differ, and
 * **nothing may render the two as one figure.**
 */
@Entity
@Table(name = "arrival_step_states")
class ArrivalStepState(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    /** The [ArrivalStep.key] this settles. Deliberately not a foreign key — see the class KDoc. */
    @Column(name = "step_key", nullable = false)
    val stepKey: String,
    /**
     * The scope of the step this settles: null for a company-wide step.
     *
     * Carried so that a project-scoped step and a company-wide step sharing a key remain separate
     * facts. ⚠️ As on [ArrivalStep], null-versus-null means the uniqueness of
     * `(user_id, step_key, project_id)` needs two partial indexes plus service-level enforcement.
     */
    @Column(name = "project_id", nullable = true)
    val projectId: UUID? = null,
    /** How this hire's step was established: observed by the system, or declared by them. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val rigor: Rigor,
    @Column(name = "settled_at", nullable = false)
    val settledAt: Instant = Instant.now(),
)
