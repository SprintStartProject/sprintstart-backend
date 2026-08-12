package com.sprintstart.sprintstartbackend.onboarding.model.response.track

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind

/**
 * One onboarding track, as a PM choosing one for a role sees it.
 *
 * [evidenceKinds] is the honest part of this response: a track with none cannot have its hires'
 * work observed by anything connected today, so a PM assigning it should know that when they
 * choose, not discover it weeks later when the hire's ramp has never moved.
 */
data class OnboardingTrackResponse(
    val key: String,
    val label: String,
    val contributionNoun: String,
    val contributionNounPlural: String,
    val contributionVerbPast: String,
    val evidenceKinds: List<ContributionEvidenceKind>,
)
