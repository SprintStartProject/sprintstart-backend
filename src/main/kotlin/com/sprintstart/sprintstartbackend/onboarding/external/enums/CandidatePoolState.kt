package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Whether a browsable corpus issue is already in the starter-work pool, and how it got there.
 *
 * This describes the pool, never the issue's suitability. Nothing here ranks or filters
 * anything: it exists so a browser can say *why* an issue is not offered for promotion, instead of
 * leaving it out. An issue a person cannot find has no way of telling them whether it was filtered
 * or never ingested — the same reason mining reports what it left out rather than quietly returning
 * less.
 *
 * [REMOVED] is the one that must stay visible. Rejection is sticky by design, so an issue somebody
 * took out of the pool cannot be promoted back in; showing it as absent would read as a bug and
 * invite somebody to look for it forever.
 */
enum class CandidatePoolState {
    /**
     * Not on offer to anybody — the only state promotion accepts.
     *
     * Covers an issue with no proposal at all and one whose proposal went stale because the issue
     * had closed. Promotion takes both: the first creates a row, the second revives one, and from
     * a PM's side the click means the same thing either way.
     */
    AVAILABLE,

    /** Already a live starter-work task, mined or picked. Promoting it again would duplicate it. */
    IN_POOL,

    /** Somebody took it out of the pool. Sticky: mining never re-proposes it and neither does this. */
    REMOVED,
}
