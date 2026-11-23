package com.sprintstart.sprintstartbackend.starterwork.external

import com.sprintstart.sprintstartbackend.path.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceCalls
import com.sprintstart.sprintstartbackend.starterwork.external.model.AssembleOrientationRequest
import com.sprintstart.sprintstartbackend.starterwork.external.model.OrientationOutcome
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Component
import java.util.UUID

/** The AI service's assembly of orientation packets for starter-work tasks. */
@Component
class OrientationAiClient(
    private val calls: AiServiceCalls,
) {
    /**
     * Assembles the orientation packet for one task from the project's existing material.
     *
     * On a hire's request path: the caller caches the result against the task and sends
     * [lastFingerprint] on every read, so an unchanged corpus comes back `unchanged` with no
     * retrieval or LLM pass, and a corpus that has moved is re-assembled. [labels] and
     * [touchedPaths] aim retrieval.
     *
     * Nothing about the individual hire is sent — two people who claim the task read the same
     * packet.
     *
     * `skipped` with no packet is a real answer and must reach the hire as an honest empty state,
     * never a fabricated packet.
     */
    suspend fun assembleOrientation(
        taskTitle: String,
        projectIds: List<UUID>,
        taskBody: String = "",
        labels: List<String> = emptyList(),
        touchedPaths: List<String> = emptyList(),
        lastFingerprint: String? = null,
    ): OrientationOutcome =
        calls.post(
            "/api/v1/onboarding/orientation",
            AssembleOrientationRequest(
                projectIds = projectIds.map { it.toString() },
                taskTitle = taskTitle,
                taskBody = taskBody,
                labels = labels,
                touchedPaths = touchedPaths,
                lastFingerprint = lastFingerprint,
            ),
            "assemble orientation",
        )

    /**
     * Streams the AI service assembling an orientation packet.
     *
     * The streaming twin of [assembleOrientation]: same inputs and same result, but the AI emits
     * [AiProgressEvent]s as it works (a `stage` per retrieval step, an `item` per grounded section,
     * a terminal `done` carrying the whole outcome). An `error` chunk is not turned into an
     * exception — it is a terminal event the caller relays to the browser. The persisted packet is
     * taken from the `done` event's `result`, so it is byte-for-byte what the cached call returns.
     */
    fun streamOrientation(
        taskTitle: String,
        projectIds: List<UUID>,
        taskBody: String = "",
        labels: List<String> = emptyList(),
        touchedPaths: List<String> = emptyList(),
        lastFingerprint: String? = null,
    ): Flow<AiProgressEvent> =
        calls.stream(
            "/api/v1/onboarding/orientation/stream",
            AssembleOrientationRequest(
                projectIds = projectIds.map { it.toString() },
                taskTitle = taskTitle,
                taskBody = taskBody,
                labels = labels,
                touchedPaths = touchedPaths,
                lastFingerprint = lastFingerprint,
            ),
            "AI progress",
        )
}
