package com.sprintstart.sprintstartbackend.connectors.notion.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * Stores the last successfully ingested version of a page within a workspace connection.
 *
 * This state drives incremental sync independently for every page. `unlinkedAt` records confirmed
 * removal from the credential's visible scope while preserving enough identity to restore the same
 * canonical artifact if the page becomes visible again.
 */
@Entity
@Table(
    name = "notion_synced_pages",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_notion_synced_page",
            columnNames = ["connection_id", "page_id"],
        ),
    ],
)
internal class NotionSyncedPage(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) val connectionId: UUID,
    @Column(nullable = false) val pageId: String,
    @Column(nullable = false, length = 2000) var pageTitle: String,
    @Column(nullable = false, length = 2048) var pageUrl: String,
    var lastEditedTime: Instant? = null,
    @Column(length = 64) var contentHash: String? = null,
    var lastSyncedAt: Instant? = null,
    var unlinkedAt: Instant? = null,
)
