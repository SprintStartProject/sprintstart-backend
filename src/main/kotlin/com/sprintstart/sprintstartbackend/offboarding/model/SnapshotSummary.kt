package com.sprintstart.sprintstartbackend.offboarding.model

import java.time.Instant
import java.util.UUID

/**
 * What a stored risk map is, without the ranking.
 *
 * Read straight from the table, so listing many maps never loads their rankings.
 *
 * @property id The snapshot's identity.
 * @property departing The leaving people, one per line.
 */
data class SnapshotRow(
    val id: UUID,
    val label: String?,
    val computedAt: Instant,
    val asOf: Instant,
    val departing: String,
    val profileName: String,
    val files: Int,
    val orphaned: Int,
    val hoarded: Int,
) {
    /** The row as a summary. */
    fun toSummary() = SnapshotSummary(
        id = RiskSnapshotId(id),
        label = label,
        computedAt = computedAt,
        asOf = asOf,
        departing = departing.lines().filter { it.isNotBlank() },
        profileName = profileName,
        files = files,
        orphaned = orphaned,
        hoarded = hoarded,
    )
}

/**
 * What a stored risk map is, without the ranking.
 *
 * @property label A name the manager gave it, if they did.
 * @property computedAt When the map was computed.
 * @property asOf The date the history was replayed as of.
 * @property departing The leaving people.
 * @property profileName The weight profile it was computed with.
 * @property files How many files with recorded history it covers.
 * @property orphaned How many files lose their last author.
 * @property hoarded How many files are left with one author.
 */
data class SnapshotSummary(
    val id: RiskSnapshotId,
    val label: String?,
    val computedAt: Instant,
    val asOf: Instant,
    val departing: List<String>,
    val profileName: String,
    val files: Int,
    val orphaned: Int,
    val hoarded: Int,
)
