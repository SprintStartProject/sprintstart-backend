package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintResourceRepository : JpaRepository<BlueprintResource, UUID> {
    fun findAllByBlueprintStepId(blueprintStepId: UUID): MutableList<BlueprintResource>
}
