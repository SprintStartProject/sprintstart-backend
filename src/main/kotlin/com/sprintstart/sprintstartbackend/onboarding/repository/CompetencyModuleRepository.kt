package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CompetencyModuleRepository : JpaRepository<CompetencyModule, UUID> {
    fun findByCompetencyKeyAndProjectIdAndStatus(
        competencyKey: String,
        projectId: UUID,
        status: ModuleStatus,
    ): CompetencyModule?

    /** The live modules for a project, which is what a hire's path nodes point at. */
    fun findAllByProjectIdAndStatus(projectId: UUID, status: ModuleStatus): List<CompetencyModule>

    fun findAllByProjectIdAndStatusOrderByCompetencyKeyAsc(
        projectId: UUID,
        status: ModuleStatus,
    ): List<CompetencyModule>

    fun findAllByCompetencyKeyAndProjectIdOrderByVersionDesc(
        competencyKey: String,
        projectId: UUID,
    ): List<CompetencyModule>
}
