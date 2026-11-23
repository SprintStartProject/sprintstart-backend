package com.sprintstart.sprintstartbackend.offboarding.model.response

import java.time.Instant

/** The risk map of a simulated departure. */
data class RiskMapResponse(
    val asOf: Instant,
    val settings: RiskSettingsResponse,
    val departing: List<String>,
    val unknownDeparting: List<String>,
    val summary: RiskSummaryResponse,
    val files: List<FileRiskResponse>,
)

/** The settings a ranking was computed with. */
data class RiskSettingsResponse(
    val halfLifeDays: Double,
    val inactiveAfterDays: Long,
    val authorThreshold: Double,
    val knowledgeWeight: Double,
    val activityWeight: Double,
)

/** How many files fall in each class; `listed` is how many of the at-risk files `files` carries. */
data class RiskSummaryResponse(
    val files: Int,
    val orphaned: Int,
    val hoarded: Int,
    val alreadyOrphaned: Int,
    val listed: Int,
)

/** One file at risk, with the measures that put it there. */
data class FileRiskResponse(
    val repository: String,
    val path: String,
    val level: String,
    val score: Double,
    val departingShare: Double,
    val recentActivity: Double,
    val fragmentation: Double,
    val remainingKnowers: List<String>,
    val lastChangedAt: Instant,
)
