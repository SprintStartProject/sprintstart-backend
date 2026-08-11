package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintPhaseRepository : JpaRepository<BlueprintPhase, UUID> {
    fun countByBlueprintPathId(blueprintPathId: UUID): Long

    fun findAllByBlueprintPathIdAndPositionGreaterThanEqualOrderByPositionDesc(
        blueprintPathId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<BlueprintPhase>

    fun findAllByBlueprintPathIdAndPositionBetween(
        blueprintPathId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<BlueprintPhase>

    fun findAllByBlueprintPathId(blueprintPathId: UUID): MutableList<BlueprintPhase>
}
