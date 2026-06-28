package com.sprintstart.sprintstartbackend.onboarding.external

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateOnboardingPathRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingAiPathEvent
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

@Component
class OnboardingAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Opens an SSE stream against the AI service to generate a personalized onboarding path.
     *
     * The AI service is stateless, so the caller supplies the [blueprints] it should
     * personalize against. Malformed SSE chunks are logged and skipped rather than
     * terminating the stream.
     *
     * @param workingArea The user's working area scope (e.g. `backend`).
     * @param experience The user's self-reported experience level, or `null` if unknown.
     * @param blueprints The active blueprints the AI should personalize; empty yields a generic path.
     * @return A cold [Flow] of [OnboardingAiPathEvent]s emitted as generation progresses.
     */
    fun generatePath(
        workingArea: String,
        experience: String?,
        blueprints: List<BlueprintSchema> = emptyList(),
    ): Flow<OnboardingAiPathEvent> =
        webClient
            .post()
            .uri(uri("/api/v1/onboarding/path"))
            .body(
                GenerateOnboardingPathRequest(
                    workingArea = workingArea,
                    experience = experience,
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
     * @param scopes The scopes to (re)generate, or `null` to refresh all known scopes.
     * @param active The backend's currently-active blueprints for the requested scopes.
     * @return The per-scope generation outcomes returned by the AI service.
     */
    suspend fun generateBlueprints(
        scopes: List<String>?,
        active: List<BlueprintSchema> = emptyList(),
    ): GenerateBlueprintsResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/blueprints/generate"))
                .body(GenerateBlueprintsRequest(scopes = scopes, active = active))
                .sync()
                .perform<GenerateBlueprintsResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to generate blueprints (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    /** Builds an absolute URI for [path] against the configured AI service base URL. */
    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
