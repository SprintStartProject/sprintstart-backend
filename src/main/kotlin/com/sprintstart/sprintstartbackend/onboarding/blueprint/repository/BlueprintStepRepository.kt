package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintStepRepository : JpaRepository<BlueprintStep, UUID> {
    fun countByBlueprintPhaseId(blueprintPhaseId: UUID): Long

    fun findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(
        blueprintPhaseId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<BlueprintStep>

    fun findAllByBlueprintPhaseIdAndPositionBetween(
        blueprintPhaseId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<BlueprintStep>

    fun findByBlueprintPhaseBlueprintPathProjectIdAndId(
        projectId: UUID,
        id: UUID,
    ): BlueprintStep?

    fun findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(
        id: UUID,
    ): BlueprintStep?

    fun findAllByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(
        projectId: UUID,
        blueprintPhaseId: UUID,
    ): MutableList<BlueprintStep>

    fun findAllByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(
        blueprintPhaseId: UUID,
    ): MutableList<BlueprintStep>
}
