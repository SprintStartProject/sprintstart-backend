package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintSubGraphNode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BlueprintSubGraphNodeRepository : JpaRepository<BlueprintSubGraphNode, UUID> {
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

    @Query(
        """
        SELECT n
        FROM BlueprintSubGraphNode n
        JOIN n.blockedBy blocker
        WHERE blocker.id = :blockerId
        """,
    )
    fun findAllByBlockedBy(blockerId: UUID): MutableList<BlueprintSubGraphNode>
}
