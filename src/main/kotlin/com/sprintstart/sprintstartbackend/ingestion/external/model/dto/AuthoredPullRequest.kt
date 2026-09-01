package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.time.Instant
import java.util.UUID

/**
 * One pull request a person authored, reduced to its lifecycle.
 *
 * [firstResponseAt] is the earliest reaction from anyone else -- a review or a comment. A null
 * means nobody has responded yet, which is a finding rather than missing data: an unanswered pull
 * request is the failure onboarding instrumentation exists to catch.
 */
data class AuthoredPullRequest(
    val artifactId: UUID,
    val openedAt: Instant?,
    val firstResponseAt: Instant?,
    val mergedAt: Instant?,
    val state: String?,
    /**
     * How many reviews asked the author to change this pull request.
     *
     * Merge state alone cannot tell a clean change from one sent back three times.
     */
    val changesRequestedCount: Int = 0,
    val repositoryFullName: String? = null,
    /** The pull request's own number (e.g. 142), parsed from its source id. Null if unparseable. */
    val number: Int? = null,
    /** The pull request title, so a hire can be told *which* pull request, not just how many. */
    val title: String? = null,
    /** A link straight to the pull request on the host, when the artifact recorded one. */
    val sourceUrl: String? = null,
) {
    /**
     * Truly open: neither merged nor closed-without-merging.
     *
     * A pull request closed without merging also has a null [mergedAt], so merge state alone would
     * miscount it as open — [state] is what separates a live pull request from a closed one. Merged
     * pull requests carry a [mergedAt]; closed-unmerged ones report state `CLOSED`; only a genuinely
     * open one is neither. An unknown ([state] null) unmerged pull request is treated as open, which
     * only matters for data that predates state capture.
     */
    val isOpen: Boolean
        get() = mergedAt == null && !"CLOSED".equals(state, ignoreCase = true)
}
