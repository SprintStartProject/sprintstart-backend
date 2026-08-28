package com.sprintstart.sprintstartbackend.connectors.confluence.model.entity

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpecJpaConverter
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Persists one project-scoped Confluence Cloud space configuration.
 *
 * Project ownership is represented by a scalar UUID so this connector does not access the user
 * module's entities or repositories. The database migration supplies the project foreign key.
 */
@Entity
@Table(
    name = "confluence_space_connections",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_confluence_connection_project_tenant_space",
            columnNames = ["project_id", "base_url", "space_id"],
        ),
    ],
    indexes = [Index(name = "idx_confluence_connection_project", columnList = "project_id")],
)
internal class ConfluenceSpaceConnection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "project_id", nullable = false, updatable = false)
    var projectId: UUID,
    @Column(name = "base_url", nullable = false, length = 2048)
    var baseUrl: String,
    @Column(name = "space_id", nullable = false)
    var spaceId: String,
    @Column(name = "space_key", nullable = false)
    var spaceKey: String,
    @Column(name = "source_enabled", nullable = false)
    var sourceEnabled: Boolean = true,
    @Column(name = "auto_update", nullable = false)
    var autoUpdate: Boolean = false,
    @Column(nullable = false)
    var schedule: String = DEFAULT_CONFLUENCE_SCHEDULE,
    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = ScheduleSpecJpaConverter::class)
    var spec: ScheduleSpec = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
    @Column(name = "next_sync_at")
    var nextSyncAt: Instant? = null,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "confluence_connection_page_allowlist",
        joinColumns = [JoinColumn(name = "connection_id")],
    )
    @OrderColumn(name = "sort_order")
    @Column(name = "page_id", nullable = false)
    private var pageAllowlistInternal: MutableList<String> = mutableListOf(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "confluence_connection_page_denylist",
        joinColumns = [JoinColumn(name = "connection_id")],
    )
    @OrderColumn(name = "sort_order")
    @Column(name = "page_id", nullable = false)
    private var pageDenylistInternal: MutableList<String> = mutableListOf(),
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    @OneToOne(mappedBy = "connection", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    lateinit var credential: ConfluenceCredential

    val pageAllowlist: List<String>
        get() = pageAllowlistInternal.toList()

    val pageDenylist: List<String>
        get() = pageDenylistInternal.toList()

    fun configureCredential(email: String, apiToken: String) {
        check(!this::credential.isInitialized) { "Confluence credentials are already configured" }
        credential = ConfluenceCredential(
            email = email,
            apiToken = apiToken,
            connection = this,
        )
    }

    /** Returns whether a stable page ID is eligible under the stored filters. */
    fun allowsPage(pageId: String): Boolean {
        val normalizedPageId = pageId.trim()
        if (normalizedPageId.isEmpty() || normalizedPageId in pageDenylistInternal) {
            return false
        }
        return pageAllowlistInternal.isEmpty() || normalizedPageId in pageAllowlistInternal
    }

    @PrePersist
    fun recordCreationTime() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun recordUpdateTime() {
        updatedAt = Instant.now()
    }

    override fun toString(): String {
        return "ConfluenceSpaceConnection(" +
            "id=$id, projectId=$projectId, baseUrl=$baseUrl, spaceId=$spaceId, sourceEnabled=$sourceEnabled)"
    }
}

internal const val DEFAULT_CONFLUENCE_SCHEDULE = "0 0 2 * * *"
