package com.sprintstart.sprintstartbackend.onboarding.external

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.onboarding.external.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiOnboardingEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.Blueprint
import com.sprintstart.sprintstartbackend.onboarding.external.model.BlueprintDiff
import com.sprintstart.sprintstartbackend.onboarding.external.model.DraftListResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.GenerateBlueprintsResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingPathRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.RollbackBlueprintRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.VersionListResponse
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Component
import java.net.URI

@Component
class OnboardingAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    fun generatePath(request: OnboardingPathRequest): Flow<AiOnboardingEvent> =
        webClient
            .post()
            .uri(uri("/api/v1/onboarding/path"))
            .body(request)
            .stream()
            .perform<AiOnboardingEvent>(
                terminationMarkers = setOf("[DONE]"),
                onChunkError = { raw, err ->
                    System.err.println("OnboardingAiClient: skipping malformed SSE chunk '$raw': ${err.message}")
                    true
                },
            )

    suspend fun generateBlueprints(request: GenerateBlueprintsRequest): GenerateBlueprintsResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/blueprints/generate"))
                .body(request)
                .sync()
                .perform<GenerateBlueprintsResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to generate blueprints (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    suspend fun listDrafts(): DraftListResponse =
        try {
            webClient
                .get()
                .uri(uri("/api/v1/onboarding/blueprints/drafts"))
                .sync()
                .perform<DraftListResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw OnboardingAiException(e.statusCode, e.body, "Failed to list drafts (HTTP ${e.statusCode}): ${e.body}")
        }

    suspend fun getDraftDiff(scope: String): BlueprintDiff =
        try {
            webClient
                .get()
                .uri(uri("/api/v1/onboarding/blueprints/drafts/$scope/diff"))
                .sync()
                .perform<BlueprintDiff>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to get draft diff for '$scope' (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    suspend fun approveDraft(scope: String): Blueprint =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/blueprints/drafts/$scope/approve"))
                .sync()
                .perform<Blueprint>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to approve draft '$scope' (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    suspend fun discardDraft(scope: String) {
        try {
            webClient
                .delete()
                .uri(uri("/api/v1/onboarding/blueprints/drafts/$scope"))
                .sync()
                .performRaw()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to discard draft '$scope' (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }
    }

    suspend fun listVersions(scope: String): VersionListResponse =
        try {
            webClient
                .get()
                .uri(uri("/api/v1/onboarding/blueprints/$scope/versions"))
                .sync()
                .perform<VersionListResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to list versions for '$scope' (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    suspend fun rollback(scope: String, version: String): Blueprint =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/onboarding/blueprints/$scope/rollback"))
                .body(RollbackBlueprintRequest(version = version))
                .sync()
                .perform<Blueprint>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to rollback '$scope' to version '$version' (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
