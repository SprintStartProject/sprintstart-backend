package com.sprintstart.sprintstartbackend.offboarding.model.mapper

import com.sprintstart.sprintstartbackend.offboarding.model.SnapshotSummary
import com.sprintstart.sprintstartbackend.offboarding.model.StoredComparison
import com.sprintstart.sprintstartbackend.offboarding.model.StoredRiskMap
import com.sprintstart.sprintstartbackend.offboarding.model.response.FileMovementResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskComparisonResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSnapshotResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSnapshotSummaryResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileMovement
import kotlin.math.round

private const val THOUSAND = 1000.0

/** Converts a stored risk map to its response, listing at most [limit] files. */
fun StoredRiskMap.toResponse(limit: Int): RiskSnapshotResponse {
    val listed = map.files.take(limit)
    return RiskSnapshotResponse(
        snapshot = summary.toResponse(),
        map = map.copy(files = listed, summary = map.summary.copy(listed = listed.size)),
    )
}

/** Converts a stored risk map's summary to its response. */
fun SnapshotSummary.toResponse() = RiskSnapshotSummaryResponse(
    id = id.value,
    label = label,
    computedAt = computedAt,
    asOf = asOf,
    departing = departing,
    profileName = profileName,
    files = files,
    orphaned = orphaned,
    hoarded = hoarded,
)

/** Converts a comparison to its response, listing at most [limit] movements. */
fun StoredComparison.toResponse(limit: Int) = RiskComparisonResponse(
    left = left.toResponse(),
    right = right.toResponse(),
    onlyLeft = comparison.onlyLeft,
    onlyRight = comparison.onlyRight,
    both = comparison.both,
    topOverlap = rounded(comparison.topOverlap),
    rankCorrelation = comparison.rankCorrelation?.let(::rounded),
    movements = comparison.movements.take(limit).map { it.toResponse() },
)

private fun FileMovement.toResponse() = FileMovementResponse(
    repository = repository,
    path = path,
    leftRank = leftRank,
    rightRank = rightRank,
    leftScore = leftScore?.let(::rounded),
    rightScore = rightScore?.let(::rounded),
    leftLevel = leftLevel,
    rightLevel = rightLevel,
)

private fun rounded(value: Double): Double = round(value * THOUSAND) / THOUSAND
