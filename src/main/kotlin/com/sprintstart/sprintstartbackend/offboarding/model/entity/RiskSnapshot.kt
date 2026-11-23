package com.sprintstart.sprintstartbackend.offboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A risk map as it was computed, kept so it can be read again and compared with another.
 *
 * Rankings change when the history, the confirmed identity links or the settings change, so a
 * departure that is evaluated later cannot be recomputed to what a manager saw. A snapshot is never
 * changed after it is stored; the whole ranking is kept as the response that was served, and the
 * counts a listing needs are kept next to it so a listing never has to read the ranking.
 *
 * @property id The row's identity, assigned when the row is first saved, so it is `null` only on a
 *           row that has not been stored yet.
 * @property projectId The project the map was computed for.
 * @property label A name the manager gave it, such as the weight profile under test.
 * @property times When the map was computed and which date it replays.
 * @property departing The leaving people, one per line.
 * @property profileName The name of the weight profile it was computed with.
 * @property counts How many files the map covers and how many are at risk.
 * @property payload The ranking, as the JSON of the response that was served.
 */
@Entity
@Table(
    name = "risk_snapshot",
    indexes = [Index(name = "idx_risk_snapshot_project", columnList = "project_id")],
)
class RiskSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column
    val label: String?,
    @Embedded
    val times: SnapshotTimes,
    @Column(nullable = false, columnDefinition = "TEXT")
    val departing: String,
    @Column(name = "profile_name", nullable = false)
    val profileName: String,
    @Embedded
    val counts: SnapshotCounts,
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,
)

/**
 * The two dates a stored map carries.
 *
 * @property computedAt When the map was computed.
 * @property asOf The date the history was replayed as of.
 */
@Embeddable
data class SnapshotTimes(
    @Column(name = "computed_at", nullable = false)
    val computedAt: Instant,
    @Column(name = "as_of", nullable = false)
    val asOf: Instant,
)

/**
 * The size of a stored map, kept beside the ranking so a listing never has to read it.
 *
 * @property files How many files with recorded history the map covers.
 * @property orphaned How many files lose their last author.
 * @property hoarded How many files are left with one author.
 */
@Embeddable
data class SnapshotCounts(
    @Column(name = "file_count", nullable = false)
    val files: Int,
    @Column(nullable = false)
    val orphaned: Int,
    @Column(nullable = false)
    val hoarded: Int,
)
