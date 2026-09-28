package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.Instant
import java.util.UUID

/**
 * When a project's knowledge gaps were last computed, regardless of what the scan found.
 *
 * The gaps themselves cannot answer this: a scan that finds nothing writes no rows, so a project
 * with complete documentation is indistinguishable from one that was never scanned if the timestamp
 * is derived from [KnowledgeGap.refreshedAt]. That is precisely the case the panel needs to tell
 * apart — "no gaps found" and "no scan yet" are opposite messages for a PM.
 *
 * One row per project, overwritten by each scan: only the latest result is ever served.
 */
@Entity
class KnowledgeGapsScan(
    @Id
    @Column(name = "project_id")
    val projectId: UUID,
    @Column(nullable = false)
    var scannedAt: Instant = Instant.now(),
)
