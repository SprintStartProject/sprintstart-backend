package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.time.Instant

/** What a departure does to a file that has people who know it. */
enum class RiskLevel {
    /** Nobody who knows the file is left. */
    ORPHANED,

    /** One person who knows the file is left, so the next departure orphans it. */
    HOARDED,
}

/**
 * Why one file is on the risk map.
 *
 * Every number is a separate, explainable measure, so a reader can see what put the file here.
 *
 * @property departingShare The fraction of the file's changes made by the leaving people, from 0 to 1.
 * @property recentActivity How recently and often the file changes, relative to the busiest file, from 0 to 1.
 * @property fragmentation How spread the changes are across people: 0 for one person, towards 1 for many.
 * @property remainingKnowers The authors who stay, at most one.
 * @property lastChangedAt When the file last changed.
 */
data class FileRisk(
    val repository: String,
    val path: String,
    val level: RiskLevel,
    val score: Double,
    val departingShare: Double,
    val recentActivity: Double,
    val fragmentation: Double,
    val remainingKnowers: List<PersonKey>,
    val lastChangedAt: Instant,
)

/**
 * The size of the problem.
 *
 * @property files Files with recorded history as of the date.
 * @property orphaned Files that lose their last author.
 * @property hoarded Files left with exactly one author.
 * @property alreadyOrphaned Files nobody active knows, whoever leaves.
 */
data class RiskSummary(
    val files: Int,
    val orphaned: Int,
    val hoarded: Int,
    val alreadyOrphaned: Int,
)

/**
 * The outcome of simulating a departure.
 *
 * @property profile The settings the ranking was computed with.
 * @property departing Who was removed.
 * @property unknownDeparting Departing people no recorded change belongs to, which usually means the
 *           email or login was mistyped or is spelled differently in the history.
 * @property files The files at risk, most urgent first.
 */
data class RiskMap(
    val asOf: Instant,
    val profile: ScoringProfile,
    val departing: Set<PersonKey>,
    val unknownDeparting: Set<PersonKey>,
    val summary: RiskSummary,
    val files: List<FileRisk>,
)

/** A departure to simulate: who leaves, on which date, and how to weigh the evidence. */
data class DepartureQuery(
    val departing: Set<PersonKey>,
    val asOf: Instant,
    val profile: ScoringProfile = ScoringProfile(),
)
