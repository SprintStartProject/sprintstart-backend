package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * Where one claim in a [TaskOrientationSection] came from.
 *
 * Stored, not derived: the whole difference between assembled orientation and generated prose is
 * that a reader can follow the claim back to the material it restates. Dropping these on persist
 * would leave the packet indistinguishable from something the model made up — which is exactly what
 * happens to a [ModulePage]'s citations today, and is worth fixing there separately.
 */
@Entity
@Table(name = "task_orientation_citations")
class TaskOrientationCitation(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "section_id", nullable = false)
    val section: TaskOrientationSection,
    /** The ingested document this came from, as the AI service named it. */
    @Column(nullable = false)
    val filename: String,
    /** The chunk id, kept so a later corpus can be checked against what was actually cited. */
    @Column(name = "chunk_id", nullable = false)
    val chunkId: String,
    /** Where the hire can open it. Null when the source has no addressable location. */
    @Column(name = "source_url", nullable = true)
    val sourceUrl: String? = null,
    @Column(nullable = false)
    val position: Int,
)
