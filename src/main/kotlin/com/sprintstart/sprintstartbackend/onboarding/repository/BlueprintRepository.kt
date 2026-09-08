package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.Blueprint
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * All lookups are project-scoped: a blueprint belongs to exactly one project, and one generated
 * before project separation (`projectId == null`) must never be reached through these queries —
 * its steps came from an unscoped corpus.
 */
interface BlueprintRepository : JpaRepository<Blueprint, UUID> {
    fun findAllByProjectIdAndStatus(projectId: UUID, status: BlueprintStatus): List<Blueprint>

    fun findByProjectIdAndScopeAndStatus(projectId: UUID, scope: String, status: BlueprintStatus): Blueprint?

    fun findAllByProjectIdAndScopeAndStatus(
        projectId: UUID,
        scope: String,
        status: BlueprintStatus,
    ): List<Blueprint>

    fun findByProjectIdAndScopeAndStatusAndVersion(
        projectId: UUID,
        scope: String,
        status: BlueprintStatus,
        version: String,
    ): Blueprint?
}
