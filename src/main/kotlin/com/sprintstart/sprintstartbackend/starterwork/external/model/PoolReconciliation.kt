package com.sprintstart.sprintstartbackend.starterwork.external.model

/**
 * What one pass changed. Returned rather than only logged so a caller — a test, or an admin
 * endpoint later — can assert on it.
 */
data class PoolReconciliation(
    val examined: Int,
    val markedStale: Int,
    val revived: Int,
    val assigneeChanged: Int,
    /**
     * Rows whose issue the corpus no longer holds, so nothing could be compared.
     *
     * Counted separately because it is the one number here that is a problem rather than a
     * result: a pool row whose source has vanished stays claimable forever on the strength of
     * whatever was true when it was mined.
     */
    val skipped: Int = 0,
)
