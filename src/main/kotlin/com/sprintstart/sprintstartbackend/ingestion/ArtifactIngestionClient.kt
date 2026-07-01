package com.sprintstart.sprintstartbackend.ingestion

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.RunArtifactsIngestResponse
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * HTTP wrapper for the AI artifact ingestion endpoint.
 *
 * Responsibilities:
 * - Build URIs from the configured base URL
 * - Send the batched ingest/deindex request to AI
 *
 * Not responsible for:
 * - Any HTTP mechanics (that's [WebClient])
 * - Any business logic (that's the service layer above)
 */
@Component
class ArtifactIngestionClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Sends a batched artifact sync request to the AI ingestion service.
     *
     * @param body The artifacts to ingest and artifact ids to remove from the AI index.
     * @return The AI ingestion result for the synchronized artifacts.
     * @throws IngestionResponseException when the AI service returns a non-successful HTTP response.
     */
    suspend fun ingest(
        body: RunArtifactsAiSyncRequest,
    ): RunArtifactsIngestResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/ingest/sync"))
                .body(body)
                .sync()
                .perform<RunArtifactsIngestResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw IngestionResponseException("Failed to ingest artifact (HTTP ${e.statusCode}): ${e.body}")
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
