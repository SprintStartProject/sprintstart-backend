package com.sprintstart.sprintstartbackend.onboarding.external

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerItem
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswerResult
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswersRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.GradeAnswersResponse
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Client for the AI service's semantic grading of free-text knowledge-check answers.
 *
 * Short-text answers are graded in a single batch call rather than one request per
 * question. A non-2xx response is wrapped in an [OnboardingAiException]; callers are
 * expected to fall back to a deterministic comparison when the AI service is
 * unavailable so that submitting an attempt never fails on grading alone.
 */
@Component
class PhaseCheckAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Grades the given short-text [items] semantically and returns one result per
     * item, correlated by [GradeAnswerItem.id].
     *
     * @param items The short-text answers to grade; the id carries the questionId.
     * @return The per-answer grading results in the AI service's response order.
     * @throws OnboardingAiException When the AI service responds with a non-2xx status.
     */
    suspend fun gradeAnswers(items: List<GradeAnswerItem>): List<GradeAnswerResult> =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/grade-answers"))
                .body(GradeAnswersRequest(answers = items))
                .sync()
                .perform<GradeAnswersResponse>()
                .results
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            val msg = "Failed to grade short-text answers (HTTP ${e.statusCode}): ${e.body}"
            throw OnboardingAiException(e.statusCode, e.body, msg)
        }

    /** Builds an absolute URI for [path] against the configured AI service base URL. */
    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
