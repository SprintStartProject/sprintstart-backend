package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.Verification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VerificationRepository : JpaRepository<Verification, UUID> {
    fun findByModuleId(moduleId: UUID): Verification?

    fun findAllByModuleIdIn(moduleIds: Collection<UUID>): List<Verification>

    fun findAllByCompetencyKeyIn(competencyKeys: Collection<String>): List<Verification>
}
