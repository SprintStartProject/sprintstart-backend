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
    /**
     * Whether an issue is still open at its source: `"OPEN"` / `"CLOSED"`, null for anything that
     * is not an issue. Refreshed unconditionally on every re-fetch, independent of the content
     * hash, since a state change alone doesn't move title/body.
     *
     * ⚠️ **Tracker-neutral, and it had to become so.** It was GitHub's issue state and nothing set
     * it for Jira — which made every ingested Jira issue invisible to starter-work mining, since
     * the miner's candidate filter is `state == "OPEN"`. It failed *silently*: a project whose
     * tracker is Jira mined an empty pool, which reads as "no good first issues here" rather than
     * "we cannot see your tracker at all". Jira's own vocabulary is a status *category*, so
     * `JiraArtifactMapper` folds it: `Done` is closed, everything else is open.
     */
    var state: String? = null,
    /**
     * Whether somebody at the source is already assigned to this issue — **three-valued on
     * purpose**, and null means *we do not know*.
     *
     * Starter work is work a hire can take, and an issue somebody else is already on is not
     * available however open it is. Only a definite `true` withholds it.
     *
     * ⚠️ **Null is not "nobody".** GitHub issues have assignees too; this system simply does not
     * ingest them, and recording that absence as "free" would be the same defect as reading an
     * absent GitHub history as "beginner". A two-valued flag would have had to guess for every
     * GitHub issue in the corpus, so engineering behaviour is left byte-identical by leaving it
     * unknown.
     */
    @Column(name = "has_assignee")
    var hasAssignee: Boolean? = null,
    // `var` because Jira re-ingestion refreshes an issue's metadata in place.
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
    // Replaced wholesale (clear + addAll) on re-fetch, not unioned -- a removed label must
    // disappear.
    @ElementCollection
    @CollectionTable(
        name = "artifact_labels",
        joinColumns = [JoinColumn(name = "artifact_id")],
    )
    @Column(name = "label", nullable = false)
    val labels: MutableList<String> = mutableListOf(),
    // `var` so a crawl can backfill rows written before these were persisted at all. They are
    // not part of the AI payload, so writing one never marks the artifact for re-embedding.
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
     *
     * This is what lets a hire's own contributions be recognized: with their declared
     * `User.githubLogin`, their activity in a project's connected repositories can be identified
     * without asking GitHub anything new.
     */
    @Column(name = "author_login", nullable = true)
    var authorLogin: String? = null,
    /**
     * When a pull request was merged at the source; null for anything unmerged or not a PR.
     *
     * The north-star metric of onboarding is time-to-first-merged-PR, and until this column existed
     * the merge was fetched from GitHub and then thrown away at the mapper -- the one fact the
     * whole measurement rests on.
     */
    @Column(name = "merged_at_source")
    var mergedAtSource: Instant? = null,
    /**
     * The first time anyone other than the author responded to a pull request -- the earliest of a
     * submitted review or a comment by someone else.
     *
     * "Receiving a response" is among the most-evidenced newcomer barriers, so the wait for a first
     * response is measured directly rather than inferred from when the PR eventually merged. A
     * review and a comment both count: a newcomer does not experience them differently.
     */
    @Column(name = "first_response_at_source")
    var firstResponseAtSource: Instant? = null,
    /**
     * How many reviews on a pull request asked for changes.
     *
     * The operational definition of "can be left alone here" is a task completed with no help and
     * **no review rework** — so whether a change had to be sent back is the load-bearing half of
     * the autonomy signal, and it cannot be recovered from merge state: a pull request that went
     * through three rounds of requested changes merges exactly like one that did not.
     *
     * Zero on a pull request nobody asked changes on, and on everything that is not a pull request.
     */
    @Column(name = "changes_requested_count", nullable = false)
    var changesRequestedCount: Int = 0,
    // --- AI sync outbox (see ArtifactAiSyncState) ---------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_sync_state", nullable = false)
    var aiSyncState: ArtifactAiSyncState = ArtifactAiSyncState.PENDING,
    // The run that last marked this artifact pending -- not `ingestionRun`, which stays pinned to
    // the run that first created the row. A run's AI-sync status is derived from this column, so
    // an artifact updated by a later run reports against that later run.
    @Column(name = "ai_sync_run_id")
    var aiSyncRunId: UUID? = null,
    @Column(name = "ai_sync_attempts", nullable = false)
    var aiSyncAttempts: Int = 0,
    // Earliest time the drainer may retry this artifact; null means "eligible now".
    @Column(name = "ai_sync_next_attempt_at")
    var aiSyncNextAttemptAt: Instant? = null,
    @Column(name = "ai_sync_error", columnDefinition = "TEXT")
    var aiSyncError: String? = null,
    @Column(name = "ai_synced_at")
    var aiSyncedAt: Instant? = null,
) {
    val projectIds: Set<UUID>
        get() = projectIdsInternal.toSet()

    /**
     * Marks this artifact as owed to the AI index again, attributed to [runId].
     *
     * Called on every change that alters what the AI service would embed (content, title, issue
     * state, labels). The attempt budget and backoff are reset because this is a *new* version of
     * the artifact, not a retry of the previous one.
     */
    fun markAiSyncPending(runId: UUID) {
        aiSyncState = ArtifactAiSyncState.PENDING
        aiSyncRunId = runId
        aiSyncAttempts = 0
        aiSyncNextAttemptAt = null
        aiSyncError = null
    }

    fun addProjectIds(projectIds: Set<UUID>) {
        projectIdsInternal.addAll(projectIds)
    }

    fun addProjectId(projectId: UUID) {
        projectIdsInternal.add(projectId)
    }
}
