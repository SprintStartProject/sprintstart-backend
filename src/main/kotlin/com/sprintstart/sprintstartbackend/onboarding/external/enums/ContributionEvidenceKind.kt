package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * What kind of evidence a contribution rests on, and therefore where it came from.
 *
 * This is what makes [Rigor] legible: a contribution is [Rigor.OBSERVED] *because* it is a merged
 * pull request and [Rigor.ATTESTED] *because* a named person confirmed it, rather than by
 * assertion. It is also the discriminator each new source is added to rather than around.
 */
enum class ContributionEvidenceKind {
    /** A pull request the hire authored, seen through ingestion. Accepted means merged. */
    PULL_REQUEST,

    /**
     * An issue assigned to the hire in a connected tracker, accepted when somebody else moved it to
     * a done status.
     *
     * As strong as [PULL_REQUEST] and for the same reason — a system recorded it, and the hire
     * could not have produced the acceptance themselves. It is what turns attestation back into
     * observation for the roles that never open a pull request: the honest end state the role-track
     * design named, rather than a shortcut around the human step.
     *
     * ⚠️ **Its ceiling is attribution, not the tracker.** Ingested issues carry only the assignee's
     * *display name*, so this is exactly as trustworthy as a hire typing their own name correctly —
     * see `User.jiraDisplayName`.
     */
    TRACKED_ISSUE,

    /**
     * Work a named accountable person confirmed happened and met the bar.
     *
     * Weaker than [PULL_REQUEST] and honestly labelled so: nothing observed it, a person vouched
     * for it. It exists because most roles produce nothing any connected system can see, and the
     * alternative to a person vouching is those roles never finishing onboarding at all.
     */
    ATTESTATION,
}
