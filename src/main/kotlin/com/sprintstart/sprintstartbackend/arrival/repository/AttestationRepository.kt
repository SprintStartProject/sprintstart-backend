package com.sprintstart.sprintstartbackend.arrival.repository

import com.sprintstart.sprintstartbackend.arrival.external.enums.AttestationState
import com.sprintstart.sprintstartbackend.arrival.model.entity.Attestation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AttestationRepository : JpaRepository<Attestation, UUID> {
    fun findAllByHireIdAndProjectId(hireId: UUID, projectId: UUID): List<Attestation>

    fun findAllByAttesterIdAndState(attesterId: UUID, state: AttestationState): List<Attestation>

    fun deleteAllByHireId(hireId: UUID)
}
