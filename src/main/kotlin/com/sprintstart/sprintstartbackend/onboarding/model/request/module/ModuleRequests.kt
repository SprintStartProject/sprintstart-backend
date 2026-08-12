package com.sprintstart.sprintstartbackend.onboarding.model.request.module

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind
import java.util.UUID

/**
 * Creates a new module version for one competency in one project.
 *
 * The version number is assigned by the server (previous highest + 1) rather than supplied: a
 * client picking its own would race another author into a duplicate.
 */
data class CreateCompetencyModuleRequest(
    val competencyKey: String,
    val projectId: UUID,
    val title: String,
    val summary: String? = null,
    /** Copy the pages and check of the current ACTIVE version, so an edit starts from what is live. */
    val copyFromActive: Boolean = false,
)

data class UpdateCompetencyModuleRequest(
    val title: String? = null,
    val summary: String? = null,
)

data class CreateModulePageRequest(
    val kind: ModulePageKind,
    val title: String,
    val body: String? = null,
    /** Insert position; appended to the end when omitted. */
    val position: Int? = null,
)

data class UpdateModulePageRequest(
    val kind: ModulePageKind? = null,
    val title: String? = null,
    val body: String? = null,
)

/**
 * Reorders a module's pages in one call.
 *
 * One ordered list rather than N per-page position writes: a sequence of single-page moves has
 * intermediate states that are not valid orderings, and a client that fails halfway leaves the
 * module scrambled.
 */
data class ReorderModulePagesRequest(
    val pageIds: List<UUID>,
)

data class RejectCompetencyModuleRequest(
    val reason: String? = null,
)
