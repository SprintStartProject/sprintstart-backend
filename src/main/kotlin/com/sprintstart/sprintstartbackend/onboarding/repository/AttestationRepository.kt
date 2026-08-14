package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.AttestationState
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Attestation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttestationRepository : JpaRepository<Attestation, UUID> {
    fun findAllByHireIdAndProjectId(hireId: UUID, projectId: UUID): List<Attestation>

    fun findAllByAttesterIdAndState(attesterId: UUID, state: AttestationState): List<Attestation>
}
