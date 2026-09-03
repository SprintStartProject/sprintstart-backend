package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintResourceRepository : JpaRepository<BlueprintResource, UUID> {
    fun findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(
        projectId: UUID,
        stepId: UUID,
    ): MutableList<BlueprintResource>

    fun findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintStepId(
        stepId: UUID,
    ): MutableList<BlueprintResource>

    fun findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(
        projectId: UUID,
        id: UUID,
    ): BlueprintResource?

    fun findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(
        id: UUID,
    ): BlueprintResource?
}
