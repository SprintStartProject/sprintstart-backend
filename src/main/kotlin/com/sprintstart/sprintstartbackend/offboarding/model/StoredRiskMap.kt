package com.sprintstart.sprintstartbackend.offboarding.model

import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.MapComparison

/**
 * A stored risk map with its ranking.
 *
 * @property summary What the map is.
 * @property map The ranking as it was served, every file at risk included.
 */
data class StoredRiskMap(
    val summary: SnapshotSummary,
    val map: RiskMapResponse,
)

/**
 * Two stored risk maps of one project and how far their rankings agree.
 *
 * @property left The map compared from.
 * @property right The map compared with.
 */
data class StoredComparison(
    val left: SnapshotSummary,
    val right: SnapshotSummary,
    val comparison: MapComparison,
)
