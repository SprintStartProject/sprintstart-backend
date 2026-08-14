package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * One piece of existing material a [TaskOrientationPacket] drew on.
 *
 * Kept as the packet's own statement rather than derived from its sections' citations, because the
 * two answer different questions: a citation says "this claim came from here", while this says
 * "this is the ground the packet stands on" — which stays useful when a section a hire wanted was
 * dropped for want of grounding, and gives "this is out of date" somewhere to point.
 */
@Entity
@Table(name = "task_orientation_sources")
class TaskOrientationSource(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "packet_id", nullable = false)
    val packet: TaskOrientationPacket,
    @Column(nullable = false)
    val filename: String,
    @Column(name = "source_url", nullable = true)
    val sourceUrl: String? = null,
    /** e.g. `FILE`, `ISSUE`, `PULL_REQUEST` — what kind of material this is, for the reader. */
    @Column(name = "artifact_type", nullable = true)
    val artifactType: String? = null,
    @Column(nullable = false)
    val position: Int,
)
