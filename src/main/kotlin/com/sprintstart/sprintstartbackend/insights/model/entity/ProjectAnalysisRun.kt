package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One finished project analysis from the PM area: its health score and what it found.
 *
 * The analysis itself runs in the client — it reads what the dashboard already shows and turns it
 * into findings by fixed rules — so this row is a record of a result, not a job. It is kept per
 * project rather than per viewer: every PM of a project looks at the same team, inbox and gaps, so
 * "since the last run" should mean the same for all of them, on any device.
 *
 * [score] is null when a check could not run. A failed check makes no findings and the score only
 * subtracts for findings, so scoring such a run would read "could not look" as "nothing wrong".
 *
 * The findings and the checks are JSON in [payload], read and written whole and never queried
 * inside — the same bargain the dashboard layout makes. Their vocabulary (areas, severities, check
 * ids) belongs to the client.
 */
@Entity
@Table(
    name = "project_analysis_runs",
    indexes = [Index(name = "idx_project_analysis_runs_project_created", columnList = "project_id, created_at")],
)
class ProjectAnalysisRun(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(nullable = true)
    val score: Int?,
    @Column(name = "failed_checks", nullable = false)
    val failedChecks: Int,
    @Column(columnDefinition = "TEXT", nullable = false)
    val payload: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
