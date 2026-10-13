package com.sprintstart.sprintstartbackend.ingestion.model.dto

/**
 * AI index state of one artifact, as shown by the Knowledge Base status chip.
 *
 * The backend owns this enum rather than forwarding the AI's lowercase strings, so the frontend
 * gets a closed set and a new or misspelt AI value can never reach it: [fromAi] maps anything
 * unrecognised to [UNKNOWN].
 */
enum class ArtifactAiIndexStatus {
    /** Embedded and searchable by the assistant (AI `indexed`, recorded as `completed`). */
    INDEXED,

    /** Ingestion is still running. */
    PROCESSING,

    /** The last ingestion attempt failed. */
    FAILED,

    /** Removed from the index on purpose. */
    DEINDEXED,

    /** The AI holds no record, reports a value we do not know, or could not be asked. */
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps the AI's status string, case-insensitively; null or unknown values become [UNKNOWN].
         */
        fun fromAi(value: String?): ArtifactAiIndexStatus =
            entries.firstOrNull { it != UNKNOWN && it.name.equals(value?.trim(), ignoreCase = true) }
                ?: UNKNOWN
    }
}
