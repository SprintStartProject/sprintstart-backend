package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckAttempt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface PhaseCheckAttemptRepository : JpaRepository<PhaseCheckAttempt, UUID> {
    fun findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phaseId: UUID, userId: UUID): MutableList<PhaseCheckAttempt>

    /**
     * Whether the user ever passed a phase's check.
     *
     * Queried instead of reading `phase.checkAttempts` so the result also reflects an attempt
     * saved earlier in the same transaction, independently of whether that lazy collection was
     * already initialized.
     */
    fun existsByPhaseIdAndUserIdAndPassedTrue(phaseId: UUID, userId: UUID): Boolean
}
