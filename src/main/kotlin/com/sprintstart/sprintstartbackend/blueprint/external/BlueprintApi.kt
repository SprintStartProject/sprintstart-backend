package com.sprintstart.sprintstartbackend.blueprint.external

import com.sprintstart.sprintstartbackend.shared.ids.BlueprintId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId

/**
 * What other modules may read about blueprints.
 *
 * Onboarding paths are built from blueprints, so the path side reads them, never the other way
 * round. Everything returned is detached from persistence.
 */
interface BlueprintApi {
    /** The project that owns blueprint [blueprintId], or `null` for a global or unknown blueprint. */
    fun projectIdOf(blueprintId: BlueprintId): ProjectId?

    /** How many blueprints of [projectId] are active. A path is built from exactly one. */
    fun activeBlueprintCount(projectId: ProjectId): Long

    /** Snapshots of every active blueprint of [projectId]. */
    fun activeBlueprints(projectId: ProjectId): List<BlueprintSnapshot>

    /** A snapshot of blueprint [blueprintId], or `null` when it does not exist. */
    fun snapshot(blueprintId: BlueprintId): BlueprintSnapshot?
}
