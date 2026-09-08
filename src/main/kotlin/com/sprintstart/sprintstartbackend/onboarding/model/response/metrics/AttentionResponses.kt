package com.sprintstart.sprintstartbackend.onboarding.model.response.metrics

import java.util.UUID

/**
 * Why one particular hire needs a human today.
 *
 * A pull request kept waiting is somebody else's move, not the hire's; a stall is the hire's.
 * The reason states which, because a dashboard that reports both as *the hire is behind* points
 * the attention at the one person who cannot fix it.
 */
data class AttentionItemResponse(
    val hireId: UUID,
    val hireName: String,
    val reason: String,
    val severity: AttentionSeverity,
    /** How long this has been true, in days — the thing that makes it urgent or not. */
    val days: Long,
)

enum class AttentionSeverity {
    /** Somebody is blocked and waiting on another person right now. */
    BLOCKED,

    /** Drifting: no merged work, no attributable identity, no progress. */
    DRIFTING,
}

/** Who on the project is waiting or stalling, worst first. */
data class ProjectAttentionResponse(
    val projectId: UUID,
    val memberCount: Int,
    val items: List<AttentionItemResponse>,
)
