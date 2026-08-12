package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * One SSE chunk of an AI generation's live progress. The AI service emits these while it works;
 * this backend deserialises them from the upstream stream and relays them to the browser unchanged,
 * persisting the final result on `done`.
 *
 * The load-bearing rules (enforced on the AI side, honoured here):
 * * an **`item`** carries a single element that has already cleared its grounding gate — never a
 *   token, never partial JSON;
 * * the terminal **`done`** carries the whole [result], which is what gets persisted, so a streamed
 *   run produces exactly what the non-streaming call would;
 * * `stage`/`warning` are advisory — losing one costs no correctness.
 *
 * [item] and [result] are opaque [JsonElement]s: the backend does not need to understand an item to
 * relay it, and only decodes [result] (into the operation's outcome DTO) to persist it. Nulls are
 * omitted on the wire, so a `stage` event carries no `item` and vice versa.
 */
@Serializable
data class AiProgressEvent(
    val type: String,
    val operation: String? = null,
    val seq: Int? = null,
    val label: String? = null,
    val stage: String? = null,
    val item: JsonElement? = null,
    val message: String? = null,
    val result: JsonElement? = null,
) {
    companion object {
        const val DONE = "done"
        const val ERROR = "error"
        const val WARNING = "warning"

        /** A terminal success the backend synthesises when there is nothing to stream from the AI. */
        fun done(operation: String, label: String): AiProgressEvent =
            AiProgressEvent(type = DONE, operation = operation, label = label)

        /** A terminal failure the backend synthesises when the AI stream could not be served. */
        fun error(operation: String, message: String): AiProgressEvent =
            AiProgressEvent(type = ERROR, operation = operation, label = message, message = message)

        /**
         * An advisory the backend synthesises when an item it showed live is rejected on persist —
         * so a validated item that fails the backend's own gate (dedupe, existing-node check) never
         * silently vanishes from the watcher's view (invariant 1, held end-to-end).
         */
        fun warning(operation: String, message: String): AiProgressEvent =
            AiProgressEvent(type = WARNING, operation = operation, label = message, message = message)
    }
}
