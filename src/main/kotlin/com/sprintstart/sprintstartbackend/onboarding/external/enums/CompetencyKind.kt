package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * The category of a competency: one durable thing a hire can be at some level of proficiency in.
 *
 * ⚠️ **Two kinds, deliberately.** `CONTRIBUTION` must not come back: a starter task is not
 * something anybody can be assessed on, and a claimed goal points at the task itself. Nor
 * `POLICY`, `CONNECTION`, `CULTURE` or `CHECKPOINT` — nothing anywhere produced them, and an enum
 * value nothing can produce makes the model look more complete than it is.
 */
enum class CompetencyKind {
    /** A tool, language or technology. */
    SKILL,

    /** A domain or architecture idea specific to this codebase. */
    CONCEPT,
}
