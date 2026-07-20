package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import java.time.Instant
import java.util.UUID

/**
 * A component that is missing critical documentation, surfaced for project managers.
 *
 * A gap is the persisted result of an AI classification run (see issue #115): the AI service
 * inspects the ingested artifacts, determines which components lack runbooks/ADRs and other
 * critical documents, and reports the result. Each reported component becomes one [KnowledgeGap].
 * The rows are treated as a rebuildable cache — a refresh replaces the whole set. [missingTypes]
 * and [presentTypes] hold free-form document-type identifiers (for example "runbook", "adr") as
 * delivered by the AI, so new types do not require a schema change. Component ownership is not
 * stored here; it is resolved separately when the gap is served.
 */
@Entity
class KnowledgeGap(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val component: String,
    @ElementCollection
    @CollectionTable(
        name = "knowledge_gap_missing_types",
        joinColumns = [JoinColumn(name = "knowledge_gap_id")],
    )
    @Column(name = "missing_type", nullable = false)
    val missingTypes: MutableList<String> = mutableListOf(),
    @ElementCollection
    @CollectionTable(
        name = "knowledge_gap_present_types",
        joinColumns = [JoinColumn(name = "knowledge_gap_id")],
    )
    @Column(name = "present_type", nullable = false)
    val presentTypes: MutableList<String> = mutableListOf(),
    @Column(nullable = false)
    val lastUpdated: Instant,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val severity: KnowledgeGapSeverity,
    @Column(nullable = false)
    val refreshedAt: Instant = Instant.now(),
)
