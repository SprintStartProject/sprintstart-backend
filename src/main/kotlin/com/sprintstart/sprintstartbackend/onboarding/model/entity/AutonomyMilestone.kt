package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * When a hire first reached autonomy on a project, and on what.
 *
 * Almost everything about the ramp is derived on read — which stage, which task, what unlocked it —
 * because every underlying fact already lives somewhere durable. This row exists because **the
 * moment itself is not derivable**: recomputing "is autonomous now" gives a boolean, and a boolean
 * cannot be announced. The end of onboarding should be a dated event somebody can point at, for the
 * hire and for their PM, not a percentage that quietly crosses a line.
 *
 * Written once, on the first read where the condition holds, and never updated: if later work needs
 * more help, that is ordinary — it does not un-happen the day somebody first shipped a change with
 * no help and no rework. The same reasoning that keeps the competency ledger monotonic.
 */
@Entity
@Table(
    name = "autonomy_milestones",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_autonomy_milestones_hire_project", columnNames = ["hire_id", "project_id"]),
    ],
)
class AutonomyMilestone(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "hire_id", nullable = false)
    val hireId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    /** When the qualifying pull request merged — the real moment, not when we noticed. */
    @Column(name = "reached_at", nullable = false)
    val reachedAt: Instant,
    /** The pull request that proved it, so the claim can be checked rather than trusted. */
    @Column(name = "proven_by_artifact_id", nullable = true)
    val provenByArtifactId: UUID? = null,
)
