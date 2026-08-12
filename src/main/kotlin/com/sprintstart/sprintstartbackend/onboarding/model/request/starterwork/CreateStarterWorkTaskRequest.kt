package com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork

/**
 * A PM's hand-authored starter-work task, created without any AI mining.
 *
 * The origination counterpart to mining: mining over ingested tracker issues is one way to fill
 * the pool, this is the other, for a task the corpus never surfaced. A hand-authored task is born
 * reviewed and claimable immediately -- writing a task *is* vouching for it.
 *
 * It has no ingested source, so it carries no `sourceId` from the client: the service synthesises a
 * stable one. [sourceUrl] is an optional human-facing link (e.g. to the issue or PR the task
 * tracks); [competencyKeys] say what the work exercises, feeding fit-ranking, and a key that is
 * not a live competency is skipped rather than rejecting the whole task -- the tags are enrichment.
 */
data class CreateStarterWorkTaskRequest(
    val title: String,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val competencyKeys: List<String> = emptyList(),
    /** Which track this work is for. Null means it suits any role. */
    val onboardingTrackKey: String? = null,
)
