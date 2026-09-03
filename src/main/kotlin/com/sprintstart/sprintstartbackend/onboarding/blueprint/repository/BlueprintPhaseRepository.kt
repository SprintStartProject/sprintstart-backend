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

    fun findAllByBlueprintPathProjectIdAndBlueprintPathId(
        projectId: UUID,
        pathId: UUID,
    ): MutableList<BlueprintPhase>

    fun findAllByBlueprintPathProjectIdIsNullAndBlueprintPathId(blueprintPathId: UUID): MutableList<BlueprintPhase>

    fun findByBlueprintPathProjectIdAndId(projectId: UUID, id: UUID): BlueprintPhase?

    fun findByBlueprintPathProjectIdIsNullAndId(id: UUID): BlueprintPhase?
}
