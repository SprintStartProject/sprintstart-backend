package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * How a user's proficiency level for a competency was established.
 *
 * The source records the provenance of a `UserCompetencyState` entry in the durable ledger:
 * [ASSESSED] from the skill-evaluation chat, [VERIFIED] from a passed artifact/checkpoint,
 * or [DECLARED] as self-reported. It lets reconciliation and re-verification treat entries
 * differently based on how much they are trusted.
 */
enum class CompetencySource {
    ASSESSED,
    VERIFIED,
    DECLARED,
}
