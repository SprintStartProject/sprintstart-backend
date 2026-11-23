package com.sprintstart.sprintstartbackend.starterwork.external

import com.sprintstart.sprintstartbackend.path.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceCalls
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import com.sprintstart.sprintstartbackend.starterwork.external.model.MineStarterWorkRequest
import com.sprintstart.sprintstartbackend.starterwork.external.model.StarterWorkOutcome
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Component
import java.util.UUID

/** The AI service's mining of starter-work candidates from the ingested corpus. */
@Component
class StarterWorkAiClient(
    private val calls: AiServiceCalls,
) {
    /**
     * Runs the AI service's batch starter-work mining job over the ingested corpus.
     *
     * The AI service is stateless: [activeSourceIds] (issues already in the backend's pool) drive
     * dedup, and [activeCompetencyKeys] (the backend's live competency keys) ground each proposed
     * task's tags — a tag outside this set is dropped by the AI service rather than invented. A
     * non-2xx response is wrapped in an [AiServiceException] carrying the upstream status/body.
     */
    suspend fun proposeStarterWork(
        projectIds: List<UUID>,
        activeSourceIds: List<String> = emptyList(),
        activeCompetencyKeys: List<String> = emptyList(),
    ): StarterWorkOutcome =
        calls.post(
            "/api/v1/onboarding/starter-work/mine",
            mineRequest(projectIds, activeSourceIds, activeCompetencyKeys),
            "propose starter work",
        )

    /**
     * Streams the AI service mining starter-work candidates.
     *
     * The streaming twin of [proposeStarterWork]: the AI emits a `stage` per pass and an `item` per
     * task as it clears the scope-safety judgement, then a terminal `done` carrying the outcome the
     * backend persists.
     */
    fun streamStarterWork(
        projectIds: List<UUID>,
        activeSourceIds: List<String> = emptyList(),
        activeCompetencyKeys: List<String> = emptyList(),
    ): Flow<AiProgressEvent> =
        calls.stream(
            "/api/v1/onboarding/starter-work/mine/stream",
            mineRequest(projectIds, activeSourceIds, activeCompetencyKeys),
            "AI progress",
        )

    private fun mineRequest(
        projectIds: List<UUID>,
        activeSourceIds: List<String>,
        activeCompetencyKeys: List<String>,
    ) = MineStarterWorkRequest(
        projectIds = projectIds.map { it.toString() },
        activeSourceIds = activeSourceIds,
        activeCompetencyKeys = activeCompetencyKeys,
    )
}
