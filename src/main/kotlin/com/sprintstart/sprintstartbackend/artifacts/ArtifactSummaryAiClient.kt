package com.sprintstart.sprintstartbackend.artifacts

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.artifacts.model.ai.AiArtifactSummaryRequest
import com.sprintstart.sprintstartbackend.artifacts.model.ai.AiArtifactSummaryStreamMessage
import com.sprintstart.sprintstartbackend.artifacts.model.exceptions.ArtifactSummaryAiException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

/**
 * Artifacts module HTTP wrapper for the AI artifact-summary service.
 *
 * This is the only artifact-summary class that knows about HTTP or URIs. It builds URIs from the
 * configured AI base URL, maps domain types onto [WebClient] calls, and interprets SSE chunk
 * semantics (done/token/error) from the raw [AiArtifactSummaryStreamMessage]. It holds no business
 * logic (in particular, no caching); that belongs to the service layer above.
 */
@Component
class ArtifactSummaryAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
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
            .map { chunk ->
                when (chunk.type) {
                    "error" -> throw ArtifactSummaryAiException("AI responded with error: ${chunk.message}")
                    else -> chunk // stage, token, citation — pass through
                }
            }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
