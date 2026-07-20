package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckAttempt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface PhaseCheckAttemptRepository : JpaRepository<PhaseCheckAttempt, UUID> {
    fun findAllByPhaseIdAndUserIdOrderByCreatedAtDesc(phaseId: UUID, userId: UUID): MutableList<PhaseCheckAttempt>
}
