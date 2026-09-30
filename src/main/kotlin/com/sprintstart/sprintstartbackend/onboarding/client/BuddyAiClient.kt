package com.sprintstart.sprintstartbackend.onboarding.client

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.chat.models.exceptions.AiResponseException
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.AiGenerateSessionTitleRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.AiGenerateSessionTitleResponse
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import org.springframework.stereotype.Component
import java.net.URI

@Component
class BuddyAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig
) {
    suspend fun getSessionTitle(request: AiGenerateSessionTitleRequest) =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/generate-title"))
                .body(request)
                .sync()
                .perform<AiGenerateSessionTitleResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw AiResponseException("Failed to generate session title (HTTP ${e.statusCode}): ${e.body}")
        }

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
