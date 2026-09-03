package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BlueprintPathRepository : JpaRepository<BlueprintPath, UUID> {
    fun findByProjectIdAndBlueprintKeyAndStatus(
        projectId: UUID,
        blueprintKey: UUID,
        status: BlueprintStatus,
    ): MutableList<BlueprintPath>

    fun findByProjectIdNullAndBlueprintKeyAndStatus(
        blueprintKey: UUID,
        status: BlueprintStatus,
    ): MutableList<BlueprintPath>

    fun findByProjectIdAndBlueprintKeyAndVersion(
        projectId: UUID,
        blueprintKey: UUID,
        version: Int,
    ): MutableList<BlueprintPath>

    fun findByProjectIdNullAndBlueprintKeyAndVersion(
        blueprintKey: UUID,
        version: Int,
    ): MutableList<BlueprintPath>

    fun findAllByProjectIdAndBlueprintKeyOrderByVersionDesc(
        projectId: UUID,
        blueprintKey: UUID,
    ): MutableList<BlueprintPath>

    fun findAllByProjectIdNullAndBlueprintKeyOrderByVersionDesc(blueprintKey: UUID): MutableList<BlueprintPath>

    fun findAllByProjectId(projectId: UUID): MutableList<BlueprintPath>

    fun findByProjectIdAndId(projectId: UUID, id: UUID): BlueprintPath?

    fun findByProjectIdIsNullAndId(id: UUID): BlueprintPath?

    fun deleteAllByProjectIdAndBlueprintKeyAndVersionAfter(projectId: UUID, blueprintKey: UUID, versionAfter: Int)

    fun deleteAllByProjectIdIsNullAndBlueprintKeyAndVersionAfter(blueprintKey: UUID, versionAfter: Int)

    @Query(
        value = """
        SELECT DISTINCT ON (blueprint_key) *
        FROM blueprint_paths
        WHERE project_id = :projectId
        ORDER BY blueprint_key, version DESC
    """,
        nativeQuery = true,
    )
    fun findLatestVersionForEachBlueprintKeyAndProjectId(
        @Param("projectId") projectId: UUID,
    ): List<BlueprintPath>

    @Query(
        value = """
        SELECT DISTINCT ON (blueprint_key) *
        FROM blueprint_paths
        WHERE project_id IS NULL
        ORDER BY blueprint_key, version DESC
    """,
        nativeQuery = true,
    )
    fun findLatestVersionForEachBlueprintKeyAndProjectIdIsNull(): List<BlueprintPath>
}
