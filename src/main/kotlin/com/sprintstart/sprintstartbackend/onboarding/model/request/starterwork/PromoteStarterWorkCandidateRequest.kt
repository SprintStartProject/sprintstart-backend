package com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork

/**
 * Puts one browsed corpus issue into the starter-work pool.
 *
 * The hand-authored path with a source id instead of typed text: the task lands live and **reviewed**
 * for the same reason a hand-written one does — somebody looked at it and vouched for it. It is
 * emphatically **not** an approval step in front of mining, which keeps landing tasks live on its
 * own; this is a second way in, for an issue mining did not pick or has not reached.
 *
 * The title and link are taken from the ingested issue rather than sent here, so the pool cannot
 * disagree with the tracker about what an issue is called. [summary] is the promoter's own note and
 * is optional — the issue's body is deliberately not copied into it, because orientation reads that
 * text live from the corpus and a copy would quietly go stale.
 */
data class PromoteStarterWorkCandidateRequest(
    /** The backend's stable identifier for the issue, e.g. `github:org/repo:ISSUE:123`. */
    val sourceId: String,
    /** A note in the promoter's words on why this is worth a newcomer's time. Optional. */
    val summary: String? = null,
    /** What the work exercises, feeding fit-ranking. Empty is honest when nobody tagged it. */
    val competencyKeys: List<String> = emptyList(),
    /** Which track this work is for. Null means it suits any role, as every mined task does. */
    val onboardingTrackKey: String? = null,
)
