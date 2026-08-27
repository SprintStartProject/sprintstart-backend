package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintCheckOptionRepository : JpaRepository<BlueprintCheckOption, UUID> {
    fun findAllByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndBlueprintCheckQuestionId(
        projectId: UUID,
        questionId: UUID,
    ): MutableList<BlueprintCheckOption>

    fun countByBlueprintCheckQuestionId(blueprintCheckQuestionId: UUID): Long

    fun findAllByBlueprintCheckQuestionIdAndPositionGreaterThanEqualOrderByPositionDesc(
        blueprintCheckQuestionId: UUID,
        positionIsGreaterThan: Int,
    ): MutableList<BlueprintCheckOption>

    fun findAllByBlueprintCheckQuestionIdAndPositionBetween(
        blueprintCheckQuestionId: UUID,
        positionAfter: Int,
        positionBefore: Int,
    ): MutableList<BlueprintCheckOption>

    fun findByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndId(
        projectId: UUID,
        id: UUID,
    ): BlueprintCheckOption?
}
