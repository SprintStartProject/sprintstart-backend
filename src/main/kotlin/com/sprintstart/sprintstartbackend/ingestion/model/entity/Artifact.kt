package com.sprintstart.sprintstartbackend.ingestion.model.entity

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.Instant
import java.util.UUID

@Entity
class Artifact(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false)
    val sourceSystem: SourceSystem,
    @Column(name = "source_id", nullable = false)
    val sourceId: String,
    @Column(name = "source_url", length = 2048)
    val sourceUrl: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false)
    val artifactType: ArtifactType,
    @Column(columnDefinition = "TEXT")
    var title: String?,
    @Column(columnDefinition = "TEXT")
    var content: String?,
    val mime: String?,
    val language: String?,
    // Whether an issue is still open at its source: `"OPEN"` / `"CLOSED"`, null for anything that is not an issue.
    var state: String? = null,
    // Whether somebody at the source is already assigned to this issue — null means we do not know.
    @Column(name = "has_assignee")
    var hasAssignee: Boolean? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var metadata: String = "{}",
    @ElementCollection
    @CollectionTable(
        name = "artifact_projects",
        joinColumns = [JoinColumn(name = "artifact_id")],
    )
    @Column(name = "project_id", nullable = false)
    // Add companion obj to Artifact to have Artifact.create
    // to keep internal state hidden
    private val projectIdsInternal: MutableSet<UUID> = mutableSetOf(),
    // GitHub issue labels (e.g. "good first issue"); empty for non-issue artifact types.
    @ElementCollection
    @CollectionTable(
        name = "artifact_labels",
        joinColumns = [JoinColumn(name = "artifact_id")],
    )
    @Column(name = "label", nullable = false)
    val labels: MutableList<String> = mutableListOf(),
    @Column(name = "created_at_source")
    var createdAtSource: Instant?,
    @Column(name = "updated_at_source")
    var updatedAtSource: Instant?,
    @Column(name = "ingested_at", nullable = false)
    val ingestedAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingestion_run_id")
    val ingestionRun: IngestionRun,
    @Column(name = "content_hash", length = 64)
    var hash: String?,
    /**
     * GitHub login of whoever authored this artifact at the source, lower-cased.
     *
     * Set for ISSUE and PULL_REQUEST only -- those come from the GraphQL API, which returns a real
     * `author.login`. Commits are parsed from `git log --pretty=%an` (a git author *name*, not a
     * GitHub account) and files have no single author, so both stay null rather than storing
     * something that merely looks like a login.
     */
    @Column(name = "author_login", nullable = true)
    var authorLogin: String? = null,
    // When a pull request was merged at the source; null for anything unmerged or not a PR.
    @Column(name = "merged_at_source")
    var mergedAtSource: Instant? = null,
    /**
     * The first time anyone other than the author responded to a pull request -- the earliest of a
     * submitted review or a comment by someone else.
     */
    @Column(name = "first_response_at_source")
    var firstResponseAtSource: Instant? = null,
    /**
     * How many reviews on a pull request asked for changes.
     * Zero on a pull request nobody asked changes on, and on everything that is not a pull request.
     */
    @Column(name = "changes_requested_count", nullable = false)
    var changesRequestedCount: Int = 0,
) {
    val projectIds: Set<UUID>
        get() = projectIdsInternal.toSet()

    fun addProjectIds(projectIds: Set<UUID>) {
        projectIdsInternal.addAll(projectIds)
    }

    fun addProjectId(projectId: UUID) {
        projectIdsInternal.add(projectId)
    }
}
