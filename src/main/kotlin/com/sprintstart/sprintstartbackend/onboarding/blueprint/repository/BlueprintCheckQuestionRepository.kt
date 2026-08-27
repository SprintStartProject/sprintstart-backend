package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintCheckQuestionRepository : JpaRepository<BlueprintCheckQuestion, UUID> {
    fun countByBlueprintPhaseId(blueprintPhaseId: UUID): Long

    fun findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(
        blueprintPhaseId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<BlueprintCheckQuestion>

    fun findAllByBlueprintPhaseIdAndPositionBetween(
        blueprintPhaseId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<BlueprintCheckQuestion>

    fun findAllByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(
        projectId: UUID,
        blueprintPhaseId: UUID,
    ): MutableList<BlueprintCheckQuestion>

    fun findByBlueprintPhaseBlueprintPathProjectIdAndId(
        projectId: UUID,
        id: UUID,
    ): BlueprintCheckQuestion?
}
