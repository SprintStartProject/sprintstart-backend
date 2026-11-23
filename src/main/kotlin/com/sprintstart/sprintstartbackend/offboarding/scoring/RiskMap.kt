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
 * @property departingShare The fraction of the file's evidence that comes from the leaving people, from 0
 *           to 1: the per-source shares averaged with the profile's weights.
 * @property sourceBreakdown For each kind of evidence on the file, the leaving people's share of it.
 * @property recentActivity How recently and often the file changes, relative to the busiest file, from 0 to 1.
 * @property fragmentation How spread the changes are across people: 0 for one person, towards 1 for many.
 * @property concentration How unevenly the knowledge of the file is spread over people.
 * @property hotspot How complicated and how often changed the file is against the other files, from 0 to 1;
 *           `null` when its complexity was never measured.
 * @property abandonedShare The share of the lines of the file written by people who are leaving or are
 *           inactive, from 0 to 1; `null` when it was never blamed.
 * @property coupling The files that change together with this one.
 * @property successors People who could take the file over, best first; they know its surroundings.
 * @property reasons Plain sentences that say why the file is on the map; they name nobody.
 * @property remainingKnowers The authors who stay, at most one.
 * @property lastChangedAt When the file last changed.
 */
data class FileRisk(
    val repository: String,
    val path: String,
    val level: RiskLevel,
    val score: Double,
    val departingShare: Double,
    val sourceBreakdown: Map<SourceId, Double>,
    val recentActivity: Double,
    val fragmentation: Double,
    val concentration: Concentration,
    val remainingKnowers: List<PersonKey>,
    val lastChangedAt: Instant,
    val coupling: Coupling = Coupling.NONE,
    val hotspot: Double? = null,
    val abandonedShare: Double? = null,
    val successors: List<Successor> = emptyList(),
    val reasons: List<String> = emptyList(),
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
    val profile: WeightProfile,
    val departing: Set<PersonKey>,
    val unknownDeparting: Set<PersonKey>,
    val summary: RiskSummary,
    val files: List<FileRisk>,
)

/** A departure to simulate: who leaves, on which date, and how to weigh the evidence. */
data class DepartureQuery(
    val departing: Set<PersonKey>,
    val asOf: Instant,
    val profile: WeightProfile = WeightProfile(),
)
