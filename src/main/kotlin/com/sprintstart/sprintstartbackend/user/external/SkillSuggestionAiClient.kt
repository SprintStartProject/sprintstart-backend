package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import com.sprintstart.sprintstartbackend.user.external.model.SkillSuggestionRequestDto
import com.sprintstart.sprintstartbackend.user.external.model.SkillSuggestionResponseDto
import com.sprintstart.sprintstartbackend.user.model.exceptions.SkillSuggestionAiException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * User module HTTP wrapper for the AI skill suggestion service.
 *
 * Builds URIs from the configured AI base URL, maps domain types onto [WebClient] calls, and
 * translates transport failures ([WebClientException]) into a module-local domain exception
 * ([SkillSuggestionAiException]). It holds no business logic; that belongs to the service layer above.
 */
@Component
class SkillSuggestionAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Requests AI-suggested skills for a project role.
     *
     * @param request Role details, optional project/industry context, and the active skills catalog.
     * @return Suggested skills matching the role.
     * @throws SkillSuggestionAiException if the AI service returns a non-2xx status.
     */
    suspend fun suggestSkills(request: SkillSuggestionRequestDto): SkillSuggestionResponseDto =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/skills/suggest"))
                .body(request)
                .sync()
                .perform<SkillSuggestionResponseDto>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw SkillSuggestionAiException(
                statusCode = e.statusCode,
                body = e.body,
                message = "Failed to suggest skills from AI service (HTTP ${e.statusCode}): ${e.body}",
            )
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
