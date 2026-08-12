package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Lifecycle state of a [com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule].
 *
 * ⚠️ Modules are proposal-only: nothing a hire sees changes until a PM approves a version into
 * [ACTIVE]. [DRAFT] is the authoring state, [PROPOSED] awaits review,
 * [ACTIVE] is the single live module per (competency, project), and [ARCHIVED] is superseded or
 * rejected, retained for history.
 */
enum class ModuleStatus { DRAFT, PROPOSED, ACTIVE, ARCHIVED }
