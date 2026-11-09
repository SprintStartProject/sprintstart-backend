package com.sprintstart.sprintstartbackend.board.external

import com.sprintstart.sprintstartbackend.board.external.model.AssembleDiagramRequest
import com.sprintstart.sprintstartbackend.board.external.model.DiagramOutcome
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceCalls
import org.springframework.stereotype.Component
import java.util.UUID

/** The AI service's drawing of board diagrams from the project's material. */
@Component
class DiagramAiClient(
    private val calls: AiServiceCalls,
) {
    /**
     * Draws one subject from the project's own material: typed nodes, typed edges, a citation each.
     *
     * [subject] aims retrieval and is asserted nowhere: every node comes back derived from a
     * retrieved chunk and cited, and an ungrounded one is dropped along with the arrows that reached
     * it.
     *
     * [lastFingerprint] must be sent — a board card hydrates this on every page load, and an
     * unchanged corpus answers `unchanged` with no retrieval and no generation.
     *
     * `skipped` with no diagram is a real answer — an empty corpus, nothing retrieved, or too few
     * grounded and connected parts to draw. It must reach the hire as an honest empty state.
     */
    suspend fun assembleDiagram(
        subject: String,
        projectIds: List<UUID>,
        lastFingerprint: String? = null,
    ): DiagramOutcome =
        calls.post(
            "/api/v1/onboarding/diagram",
            AssembleDiagramRequest(
                projectIds = projectIds.map { it.toString() },
                subject = subject,
                lastFingerprint = lastFingerprint,
            ),
            "assemble diagram",
        )
}
