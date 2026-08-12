package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * Where one claim on a [ModulePage] came from.
 *
 * Stored, not derived — the AI grounds every page it proposes, and dropping that grounding on
 * persist left a module indistinguishable from something the model made up (the failure
 * [TaskOrientationCitation] was created to avoid for orientation packets). A PM-authored page may
 * carry none: a human naming no source is a choice, not dropped provenance.
 */
@Entity
@Table(name = "module_page_citations")
class ModulePageCitation(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    val page: ModulePage,
    /** The ingested document this came from, as the AI service named it. */
    @Column(nullable = false)
    val filename: String,
    /** The chunk id, kept so a later corpus can be checked against what was actually cited. */
    @Column(name = "chunk_id", nullable = false)
    val chunkId: String,
    /** Where the claim's source can be opened. Null when it has no addressable location. */
    @Column(name = "source_url", nullable = true)
    val sourceUrl: String? = null,
    @Column(nullable = false)
    val position: Int,
)
