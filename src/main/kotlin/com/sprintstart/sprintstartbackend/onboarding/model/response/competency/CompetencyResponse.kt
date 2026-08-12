package com.sprintstart.sprintstartbackend.onboarding.model.response.competency

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind

/**
 * One competency as a PM authoring it sees it.
 *
 * [key] is returned but never accepted as input -- a PM needs to see the identity the ledger is
 * keyed by, and needs to be told it is not what they are renaming.
 */
data class CompetencyResponse(
    val key: String,
    val label: String,
    val description: String?,
    /**
     * What this competency is about, for grouping. Null is "not grouped yet" -- a real state, not
     * a missing one.
     */
    val area: String?,
    val kind: CompetencyKind,
    val targetLevel: Int,
)

/**
 * The whole competency vocabulary, for a PM authoring it.
 *
 * ⚠️ Carries no per-user state: nothing here is met or unmet, because that is a property of a
 * person rather than of the vocabulary. A flat list, with no ordering to convey and no version to
 * resolve at.
 */
data class CompetencyGraphResponse(
    val competencies: List<CompetencyResponse>,
)

/** The outcome of removing a competency. */
data class DeleteCompetencyResponse(
    val key: String,
)
