package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import com.sprintstart.sprintstartbackend.user.external.model.AiIndustryEvaluationRequest
import com.sprintstart.sprintstartbackend.user.external.model.AiIndustryEvaluationResponse
import com.sprintstart.sprintstartbackend.user.model.exceptions.ProjectIndustryAiException
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

/**
 * User module HTTP wrapper for the AI project industry evaluation service.
 *
 * Builds URIs from the configured AI base URL, maps domain types onto [WebClient] calls, and
 * translates transport failures ([WebClientException]) into a module-local domain exception
 * ([ProjectIndustryAiException]). It holds no business logic; that belongs to the service layer above.
 */
@Component
class ProjectIndustryAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Requests an AI-supported industry evaluation for the given project.
     *
     * @param projectId Identifier of the project to evaluate.
     * @return The evaluated industry, confidence level, and grounding evidence.
     * @throws ProjectIndustryAiException if the AI service returns a non-2xx status.
     */
    suspend fun evaluateIndustry(projectId: UUID): AiIndustryEvaluationResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/projects/$projectId/industry/evaluate"))
                .body(AiIndustryEvaluationRequest(projectId = projectId.toString()))
                .sync()
                .perform<AiIndustryEvaluationResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw ProjectIndustryAiException(
                statusCode = e.statusCode,
                body = e.body,
                message = "Failed to evaluate project industry (HTTP ${e.statusCode}): ${e.body}",
            )
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
