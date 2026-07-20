package com.sprintstart.sprintstartbackend.insights

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingResponse
import com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Insights module HTTP wrapper for the AI service.
 *
 * This is the only class in the `insights` module that knows about HTTP or URIs. It builds URIs
 * from the configured AI base URL, maps domain types onto [WebClient] calls, and translates
 * transport failures ([WebClientException]) into a module-local domain exception
 * ([InsightsAiException]). It holds no business logic; that belongs to the service layer above.
 */
@Component
class InsightsAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Requests recomputed recurring-question groups from the AI service.
     *
     * The AI service performs the semantic clustering and PII redaction and returns the grouped
     * result; this call carries no question data.
     *
     * @throws InsightsAiException if the AI service returns a non-2xx status.
     */
    suspend fun groupFaqQuestions(request: AiFaqGroupingRequest): AiFaqGroupingResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/insights/faq/group"))
                .body(request)
                .sync()
                .perform<AiFaqGroupingResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw InsightsAiException("Failed to group FAQ questions (HTTP ${e.statusCode}): ${e.body}")
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
