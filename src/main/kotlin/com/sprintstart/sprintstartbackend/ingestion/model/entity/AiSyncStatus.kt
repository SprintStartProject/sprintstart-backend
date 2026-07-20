package com.sprintstart.sprintstartbackend.ingestion.model.entity

/**
 * Tracks whether a completed [IngestionRun]'s artifacts have actually reached the AI
 * service's index, separately from [IngestionRunStatus].
 *
 * [IngestionRunStatus] only reflects the local fetch-and-store stage (GitHub/upload ->
 * Postgres); it turns `COMPLETED` before the AI sync has even started. This field closes
 * that gap so API consumers can tell "saved locally" apart from "actually searchable in
 * chat".
 */
enum class AiSyncStatus {
    /** The run failed before producing anything to sync; no AI sync was attempted. */
    NOT_APPLICABLE,

    /** The run finished locally and the AI sync has been dispatched but not yet confirmed. */
    PENDING,

    /** The AI service confirmed it processed the batch (or there was nothing to send). */
    SUCCEEDED,

    /** The AI sync request failed; see the run's `aiSyncFailureReason`. */
    FAILED,
}
