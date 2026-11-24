package com.sprintstart.sprintstartbackend.offboarding.model.response

import java.time.Instant
import java.util.UUID

/**
 * What a stored risk map is, without its ranking.
 *
 * @property label A name the manager gave it, `null` when they gave none.
 * @property computedAt When it was computed.
 * @property asOf The date the history was replayed as of.
 * @property departing The leaving people.
 * @property profileName The weight profile it was computed with.
 * @property files How many files with recorded history it covers.
 * @property orphaned How many files lose their last author.
 * @property hoarded How many files are left with one author.
 */
data class RiskSnapshotSummaryResponse(
    val id: UUID,
    val label: String?,
    val computedAt: Instant,
    val asOf: Instant,
    val departing: List<String>,
    val profileName: String,
    val files: Int,
    val orphaned: Int,
    val hoarded: Int,
)

/** A stored risk map with its ranking. */
data class RiskSnapshotResponse(
    val snapshot: RiskSnapshotSummaryResponse,
    val map: RiskMapResponse,
)

/**
 * How far the rankings of two stored risk maps agree.
 *
 * @property onlyLeft Files only the left ranking lists.
 * @property onlyRight Files only the right ranking lists.
 * @property both Files both rankings list.
 * @property topOverlap Share of the union of both top lists that is in both, from 0 to 1.
 * @property rankCorrelation Spearman's correlation of the order of the shared files, from -1 to 1; `null`
 *           when fewer than two files are shared.
 * @property movements The files with the biggest change in position first; a file only one side lists comes
 *           first. At most the requested number are listed.
 */
data class RiskComparisonResponse(
    val left: RiskSnapshotSummaryResponse,
    val right: RiskSnapshotSummaryResponse,
    val onlyLeft: Int,
    val onlyRight: Int,
    val both: Int,
    val topOverlap: Double,
    val rankCorrelation: Double?,
    val movements: List<FileMovementResponse>,
)

/** Where one file stands in each of two rankings; a side that does not list it has no values. */
data class FileMovementResponse(
    val repository: String,
    val path: String,
    val leftRank: Int?,
    val rightRank: Int?,
    val leftScore: Double?,
    val rightScore: Double?,
    val leftLevel: String?,
    val rightLevel: String?,
)
