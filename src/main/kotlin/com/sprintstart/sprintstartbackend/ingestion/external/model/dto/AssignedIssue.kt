package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.time.Instant
import java.util.UUID

/**
 * One tracked issue assigned to a person, reduced to the four moments onboarding measures.
 *
 * The same four moments as [AuthoredPullRequest] — opened, first answered, accepted, sent back.
 *
 * [acceptedAt] is null when the person moved their own issue to Done. Closing your own
 * ticket is a claim, not an observation. Such an issue stays in flight rather than being downgraded
 * to a weaker acceptance: absent evidence stays "no evidence".
 */
data class AssignedIssue(
    val artifactId: UUID,
    /** When the issue became this person's — the assignment, falling back to when it was created. */
    val openedAt: Instant?,
    /** The first comment by anybody other than the assignee. */
    val firstResponseAt: Instant?,
    /** When somebody else moved it to a done status, or null — see the note above. */
    val acceptedAt: Instant?,
    /**
     * How many times somebody else moved the issue out of a status the assignee had put it in.
     *
     * The tracker equivalent of a review asking for changes. Derived from the changelog rather
     * than guessed — a flat zero would hand every tracked issue an unearned clean run.
     */
    val returnedCount: Int = 0,
    /** The issue key (e.g. `ONB-42`), so a hire can be told *which* issue. */
    val key: String? = null,
    val title: String? = null,
    val sourceUrl: String? = null,
)
