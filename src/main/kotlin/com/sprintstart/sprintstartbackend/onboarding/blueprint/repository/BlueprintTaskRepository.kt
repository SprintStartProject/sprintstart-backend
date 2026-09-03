package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming")
interface BlueprintTaskRepository : JpaRepository<BlueprintTask, UUID> {
    fun findByBlueprintStepIdAndPositionGreaterThanEqualOrderByPositionDesc(
        blueprintStepId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<BlueprintTask>

    fun findByBlueprintStepIdAndPositionBetween(
        blueprintStepId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<BlueprintTask>

    fun countByBlueprintStepId(blueprintStepId: UUID): Long

    fun findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(
        projectId: UUID,
        stepId: UUID,
    ): MutableList<BlueprintTask>

    fun findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndId(
        projectId: UUID,
        id: UUID,
    ): BlueprintTask?

    fun findByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndId(
        id: UUID,
    ): BlueprintTask?
}
