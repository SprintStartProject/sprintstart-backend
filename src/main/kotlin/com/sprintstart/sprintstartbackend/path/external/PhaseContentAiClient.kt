package com.sprintstart.sprintstartbackend.path.external

import com.sprintstart.sprintstartbackend.path.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.path.external.model.AssemblePhaseRequest
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceCalls
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Component

/** The AI service's assembly of AI-enhanced onboarding phases. */
@Component
class PhaseContentAiClient(
    private val calls: AiServiceCalls,
) {
    /**
     * Streams the AI service assembling one AI-enhanced onboarding phase's content.
     *
     * The phase carries an author's prompt rather than a fixed step list; this fills it: grounded
     * steps (with tasks and resources) plus a small knowledge check, scoped to
     * [AssemblePhaseRequest.projectId]. The AI emits [AiProgressEvent]s as it works (a `stage` per
     * retrieval step, an `item` per grounded step/question, a terminal `done` carrying the whole
     * outcome). The backend relays these to the browser and takes the persisted phase content from
     * the `done` event's `result`.
     */
    fun streamPhase(request: AssemblePhaseRequest): Flow<AiProgressEvent> =
        calls.stream("/api/v1/onboarding/phase/stream", request, "AI progress")
}
