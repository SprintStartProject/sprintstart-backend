package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintStepRepository : JpaRepository<BlueprintStep, UUID> {
    fun findAllByBlueprintPhaseId(blueprintPhaseId: UUID): MutableList<BlueprintStep>

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
}
