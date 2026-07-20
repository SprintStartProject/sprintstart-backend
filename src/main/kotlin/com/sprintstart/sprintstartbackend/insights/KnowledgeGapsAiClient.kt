package com.sprintstart.sprintstartbackend.insights

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGapsRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGapsResponse
import com.sprintstart.sprintstartbackend.insights.model.exceptions.KnowledgeGapsAiException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Insights module HTTP wrapper for the AI knowledge-gap detection service.
 *
 * This is the only knowledge-gaps class that knows about HTTP or URIs. It builds URIs from the
 * configured AI base URL, maps domain types onto [WebClient] calls, and translates transport
 * failures ([WebClientException]) into a module-local domain exception ([KnowledgeGapsAiException]).
 * It holds no business logic; that belongs to the service layer above.
 */
@Component
class KnowledgeGapsAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Requests recomputed knowledge-gap classifications from the AI service.
     *
     * The AI service inspects the ingested artifacts and returns the classified gaps; this call
     * carries no source data.
     *
     * @throws KnowledgeGapsAiException if the AI service returns a non-2xx status.
     */
    suspend fun detectKnowledgeGaps(request: AiKnowledgeGapsRequest): AiKnowledgeGapsResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/insights/knowledge-gaps/detect"))
                .body(request)
                .sync()
                .perform<AiKnowledgeGapsResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw KnowledgeGapsAiException("Failed to detect knowledge gaps (HTTP ${e.statusCode}): ${e.body}")
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
