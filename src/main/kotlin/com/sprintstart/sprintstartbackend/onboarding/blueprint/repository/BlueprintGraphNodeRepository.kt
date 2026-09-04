package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintSubGraphNode
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BlueprintGraphNodeRepository : JpaRepository<BlueprintSubGraphNode, UUID> {
    fun findByBlueprintPhaseBlueprintPathProjectIdAndId(
        projectId: UUID,
        id: UUID,
    ): BlueprintSubGraphNode?

    fun findByBlueprintPhaseBlueprintPathProjectIdIsNullAndId(
        id: UUID,
    ): BlueprintSubGraphNode?

    fun findByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(
        phaseId: UUID,
    ): MutableList<BlueprintSubGraphNode>

    fun findByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(
        projectId: UUID,
        phaseId: UUID,
    ): MutableList<BlueprintSubGraphNode>
}
