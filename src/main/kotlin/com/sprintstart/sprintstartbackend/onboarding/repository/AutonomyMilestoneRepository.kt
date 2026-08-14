package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.AutonomyMilestone
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AutonomyMilestoneRepository : JpaRepository<AutonomyMilestone, UUID> {
    fun findByHireIdAndProjectId(hireId: UUID, projectId: UUID): AutonomyMilestone?

    fun findAllByProjectId(projectId: UUID): List<AutonomyMilestone>
}
