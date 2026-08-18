package com.sprintstart.sprintstartbackend.onboarding.blueprint.repository

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BlueprintPathRepository : JpaRepository<BlueprintPath, UUID> {
    fun findByBlueprintKeyAndStatus(blueprintKey: UUID, status: BlueprintStatus): MutableList<BlueprintPath>

    fun findByBlueprintKeyAndVersion(blueprintKey: UUID, version: Int): MutableList<BlueprintPath>

    fun deleteAllByBlueprintKeyAndVersionAfter(blueprintKey: UUID, versionAfter: Int)

    @Query(
        value = """
        SELECT DISTINCT ON (blueprint_key) *
        FROM blueprint_paths
        ORDER BY blueprint_key, version DESC
    """,
        nativeQuery = true,
    )
    fun findLatestVersionForEachBlueprintKey(): List<BlueprintPath>

    fun countByBlueprintKey(blueprintKey: UUID): Long

    fun findAllByBlueprintKeyOrderByVersionDesc(blueprintKey: UUID): MutableList<BlueprintPath>
}
