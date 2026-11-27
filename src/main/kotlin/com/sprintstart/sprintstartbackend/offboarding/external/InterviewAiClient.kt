package com.sprintstart.sprintstartbackend.offboarding.external

import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewRequest
import com.sprintstart.sprintstartbackend.offboarding.model.ai.AiInterviewResponse
import com.sprintstart.sprintstartbackend.offboarding.model.exception.InterviewAiException
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * The offboarding module's HTTP wrapper for the AI service's interview planner.
 *
 * It only maps the call onto [WebClient] and turns transport failures into [InterviewAiException];
 * what is sent is decided by the service above.
 */
@Component
class InterviewAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Asks the AI service for the questions worth a leaver's time.
     *
     * @throws InterviewAiException if the AI service answers with a non-2xx status.
     */
    suspend fun planInterview(request: AiInterviewRequest): AiInterviewResponse =
        try {
            webClient
                .post()
                .uri(URI.create("${applicationConfig.ai.baseUrl}/api/v1/offboarding/interview-questions"))
                .body(request)
                .sync()
                .perform<AiInterviewResponse>()
        } catch (e: WebClientException) {
            throw InterviewAiException("Failed to plan the interview (HTTP ${e.statusCode}): ${e.body}", e)
        }
}
