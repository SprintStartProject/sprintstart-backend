package com.sprintstart.sprintstartbackend.onboarding.external

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssembleDiagramRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.AssembleOrientationRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCompactRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCompactResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.DiagramOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateOnboardingPathRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.MineStarterWorkRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingAiPathEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.OrientationOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.SkillAssessmentSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.StarterWorkOutcome
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

// One method per AI-service endpoint.
@Suppress("TooManyFunctions")
@Component
class OnboardingAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
        lastFingerprint: String? = null,
    ): DiagramOutcome =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/diagram"))
                .body(AssembleDiagramRequest(subject = subject, lastFingerprint = lastFingerprint))
                .sync()
                .perform<DiagramOutcome>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to assemble diagram (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

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
        taskBody: String = "",
        labels: List<String> = emptyList(),
        touchedPaths: List<String> = emptyList(),
        lastFingerprint: String? = null,
    ): OrientationOutcome =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/orientation"))
                .body(
                    AssembleOrientationRequest(
                        taskTitle = taskTitle,
                        taskBody = taskBody,
                        labels = labels,
                        touchedPaths = touchedPaths,
                        lastFingerprint = lastFingerprint,
                    ),
                ).sync()
                .perform<OrientationOutcome>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to assemble orientation (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    /**
     * Runs one turn of the tool-using buddy agent.
     *
     * Stateless: the backend carries [BuddyAgentRequest.messages] between turns and executes the
     * tools only it can run. A non-final response returns the pending backend-tool calls plus the
     * running message list to carry back with each tool result appended. A non-2xx response is
     * wrapped in an [OnboardingAiException] carrying the upstream status/body.
     *
     * @return Either the final answer (`final=true`) or pending backend-tool calls (`final=false`).
     */
    suspend fun buddyAgentTurn(request: BuddyAgentRequest): BuddyAgentResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/buddy/agent"))
                .body(request)
                .sync()
                .perform<BuddyAgentResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to run buddy agent turn (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    /**
     * Folds older buddy turns into the mentor's durable memory note.
     *
     * Nothing waits on this: call it after a turn, never ahead of the answer.
     *
     * A non-2xx (including the AI service's 503 for an unavailable model) is wrapped in an
     * [OnboardingAiException]. That is not a degraded success — the caller must leave its cursor
     * where it is, because advancing past messages nothing summarized would drop them from both the
     * prompt and the memory standing in for it.
     */
    suspend fun compactBuddyMemory(request: BuddyCompactRequest): BuddyCompactResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/buddy/compact"))
                .body(request)
                .sync()
                .perform<BuddyCompactResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to compact buddy memory (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    /**
     * Opens a buddy visit: the greeting arrives as it is written.
     *
     * The greeting streams as `token` chunks; the memory note and any opening action arrive on the
     * terminal `done`. A malformed chunk is skipped rather than killing the stream, matching
     * [streamProgress]. The AI service degrades to a plain welcome on its own failures, so an
     * `error` chunk is not expected here — a transport failure still surfaces to the caller.
     */
    fun streamBuddyOpen(request: BuddyOpenRequest): Flow<BuddyOpenStreamEvent> =
        webClient
            .post()
            .uri(uri("/api/v1/onboarding/buddy/open/stream"))
            .body(request)
            .stream()
            .perform<BuddyOpenStreamEvent>(
                onChunkError = { raw, err ->
                    logger.warn("Skipping malformed buddy-open chunk '{}': {}", raw, err.message)
                    true
                },
            )

    /**
     * Runs the AI service's batch starter-work mining job over the ingested corpus.
     *
     * The AI service is stateless: [activeSourceIds] (issues already in the backend's pool) drive
     * dedup, and [activeCompetencyKeys] (the backend's live competency keys) ground each proposed
     * task's tags — a tag outside this set is dropped by the AI service rather than invented. A
     * non-2xx response is wrapped in an [OnboardingAiException] carrying the upstream status/body.
     */
    suspend fun proposeStarterWork(
        activeSourceIds: List<String> = emptyList(),
        activeCompetencyKeys: List<String> = emptyList(),
    ): StarterWorkOutcome =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/starter-work/mine"))
                .body(
                    MineStarterWorkRequest(
                        activeSourceIds = activeSourceIds,
                        activeCompetencyKeys = activeCompetencyKeys,
                    ),
                ).sync()
                .perform<StarterWorkOutcome>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to propose starter work (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

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
        taskBody: String = "",
        labels: List<String> = emptyList(),
        touchedPaths: List<String> = emptyList(),
        lastFingerprint: String? = null,
    ): Flow<AiProgressEvent> =
        streamProgress(
            "/api/v1/onboarding/orientation/stream",
            AssembleOrientationRequest(
                taskTitle = taskTitle,
                taskBody = taskBody,
                labels = labels,
                touchedPaths = touchedPaths,
                lastFingerprint = lastFingerprint,
            ),
        )

    /**
     * Streams the AI service mining starter-work candidates.
     *
     * The streaming twin of [proposeStarterWork]: the AI emits a `stage` per pass and an `item` per
     * task as it clears the scope-safety judgement, then a terminal `done` carrying the outcome the
     * backend persists.
     */
    fun streamStarterWork(
        activeSourceIds: List<String> = emptyList(),
        activeCompetencyKeys: List<String> = emptyList(),
    ): Flow<AiProgressEvent> =
        streamProgress(
            "/api/v1/onboarding/starter-work/mine/stream",
            MineStarterWorkRequest(
                activeSourceIds = activeSourceIds,
                activeCompetencyKeys = activeCompetencyKeys,
            ),
        )

    /**
     * Opens an SSE stream of [AiProgressEvent]s against [path], POSTing [body].
     *
     * The reusable passthrough behind every streaming operation. A malformed chunk is logged and
     * skipped rather than killing the stream; the AI's own terminal `error` event passes straight
     * through rather than throwing.
     */
    private inline fun <reified B> streamProgress(path: String, body: B): Flow<AiProgressEvent> =
        webClient
            .post()
            .uri(uri(path))
            .body(body)
            .stream()
            .perform<AiProgressEvent>(
                onChunkError = { raw, err ->
                    logger.warn("Skipping malformed AI progress chunk '{}': {}", raw, err.message)
                    true
                },
            )

    /**
     * Opens an SSE stream against the AI service to generate a personalized onboarding path.
     *
     * The AI service is stateless, so the caller supplies the [blueprints] it should
     * personalize against. Malformed SSE chunks are logged and skipped rather than
     * terminating the stream.
     *
     * @param projectId The project the path is generated for; blueprints from other projects are
     * ignored by the AI service.
     * @param workingArea The user's working area scope (e.g. `backend`).
     * @param skills The user's leveled skill assessments; lets proficiency drive personalization.
     * @param blueprints The active blueprints the AI should personalize; empty yields a generic path.
     * @return A cold [Flow] of [OnboardingAiPathEvent]s emitted as generation progresses.
     */
    fun generatePath(
        projectId: UUID,
        workingArea: String,
        skills: List<SkillAssessmentSchema> = emptyList(),
        blueprints: List<BlueprintSchema> = emptyList(),
    ): Flow<OnboardingAiPathEvent> =
        webClient
            .post()
            .uri(uri("/api/v1/onboarding/path"))
            .body(
                GenerateOnboardingPathRequest(
                    projectId = projectId.toString(),
                    workingArea = workingArea,
                    skills = skills,
                    blueprints = blueprints,
                ),
            ).stream()
            .perform<OnboardingAiPathEvent>(
                terminationMarkers = setOf("[DONE]"),
                onChunkError = { raw, err ->
                    logger.warn("Skipping malformed SSE chunk '{}': {}", raw, err.message)
                    true
                },
            )

    /**
     * Runs the AI service's batch blueprint generation job over the ingested corpus.
     *
     * The AI service is stateless: [active] (the backend's current active blueprints)
     * drives version numbering and lets the job skip an unchanged corpus. A non-2xx
     * response is wrapped in an [OnboardingAiException] carrying the upstream status/body.
     *
     * @param projectId The project to generate for; bare scope names are qualified with it.
     * @param scopes The scopes to (re)generate, or `null` to refresh all known scopes.
     * @param active The backend's currently-active blueprints for the requested scopes.
     * @return The per-scope generation outcomes returned by the AI service.
     */
    suspend fun generateBlueprints(
        projectId: UUID,
        scopes: List<String>?,
        active: List<BlueprintSchema> = emptyList(),
    ): GenerateBlueprintsResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/blueprints/generate"))
                .body(
                    GenerateBlueprintsRequest(
                        projectId = projectId.toString(),
                        scopes = scopes,
                        active = active,
                    ),
                ).sync()
                .perform<GenerateBlueprintsResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to generate blueprints (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    /** Builds an absolute URI for [path] against the configured AI service base URL. */
    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
