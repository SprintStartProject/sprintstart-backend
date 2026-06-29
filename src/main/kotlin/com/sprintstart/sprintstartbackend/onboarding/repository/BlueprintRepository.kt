package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.Blueprint
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BlueprintStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintRepository : JpaRepository<Blueprint, UUID> {
    fun findAllByStatus(status: BlueprintStatus): List<Blueprint>

    fun findByScopeAndStatus(scope: String, status: BlueprintStatus): Blueprint?

    fun findAllByScopeAndStatus(scope: String, status: BlueprintStatus): List<Blueprint>

    fun findByScopeAndStatusAndVersion(scope: String, status: BlueprintStatus, version: String): Blueprint?
}
