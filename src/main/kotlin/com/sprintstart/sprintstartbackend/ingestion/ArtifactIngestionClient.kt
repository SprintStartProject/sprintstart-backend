package com.sprintstart.sprintstartbackend.ingestion

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.AiArtifactSummaryRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.AiArtifactSummaryStreamMessage
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.RunArtifactsIngestResponse
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.ArtifactSummaryAiException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.util.UUID

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

    /**
     * Opens an SSE stream for a summary of [artifactId].
     *
     * The returned [Flow] is cold; the connection is not opened until collection begins.
     *
     * Each emitted [AiArtifactSummaryStreamMessage] has already been filtered for type:
     * - `stage`, `token` and `citation` chunks pass through.
     * - `done` terminates the stream normally.
     * - `error` chunks terminate the stream with [ArtifactSummaryAiException].
     *
     * @throws ArtifactSummaryAiException if the AI service returns a non-2xx status at stream
     *   open, or if an `error` chunk arrives mid-stream.
     */
    fun summarizeStream(
        artifactId: UUID,
        request: AiArtifactSummaryRequest,
    ): Flow<AiArtifactSummaryStreamMessage> =
        webClient
            .post()
            .uri(uri("/api/v1/artifacts/$artifactId/summary"))
            .body(request)
            .stream()
            .perform<AiArtifactSummaryStreamMessage>()
            .catch { cause ->
                if (cause is WebClientException && cause.statusCode == 404) {
                    throw ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Artifact is still being indexed by the AI service",
                    )
                }
                throw cause
            }.map { chunk ->
                when (chunk.type) {
                    "error" -> throw ArtifactSummaryAiException("AI responded with error: ${chunk.message}")
                    else -> chunk // stage, token, citation — pass through
                }
            }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
