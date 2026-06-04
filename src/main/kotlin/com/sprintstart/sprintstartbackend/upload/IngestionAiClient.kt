package com.sprintstart.sprintstartbackend.upload

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestRequest
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestResponse
import com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * AI upload module HTTP wrapper.
 *
 * This is the *only* class in the `upload` module that knows about HTTP or URIs.
 * Everything above this layer works purely with domain types.
 *
 * Responsibilities:
 * - Build URIs from the configured base URL
 * - Map domain types onto [WebClient] calls
 *
 * Not responsible for:
 * - Any HTTP mechanics (that's [WebClient])
 * - Any business logic (that's the service layer above)
 */
@Component
class IngestionAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    suspend fun ingest(
        body: AiIngestRequest,
    ): AiIngestResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/ingest"))
                .body(body)
                .sync()
                .perform<AiIngestResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw IngestionResponseException("Failed to generate chat title (HTTP ${e.statusCode}): ${e.body}")
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
