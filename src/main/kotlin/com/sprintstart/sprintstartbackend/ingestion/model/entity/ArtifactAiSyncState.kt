package com.sprintstart.sprintstartbackend.ingestion.model.entity

/**
 * Whether one [Artifact] has reached the AI service's index in its current form.
 *
 * This is the per-artifact half of [AiSyncStatus], which only tracks a whole run. It makes the
 * artifact table an outbox: an artifact is marked [PENDING] the moment it is created *or* changed,
 * a drainer sends pending artifacts in small batches while the crawl is still running, and each
 * artifact is acknowledged individually. A failed sync therefore only ever costs a retry of the
 * artifacts that actually failed, instead of the whole run's batch.
 */
enum class ArtifactAiSyncState {
    /** Stored locally but not (or no longer) reflected in the AI index; owed to the drainer. */
    PENDING,

    /** The AI service confirmed this artifact's current content is indexed. */
    SYNCED,

    /** Sync kept failing and the attempt budget is exhausted; see the artifact's `aiSyncError`. */
    FAILED,
}
