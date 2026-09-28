package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.model.AiIndustryEvaluationResponse
import java.util.UUID

/**
 * Exported API for evaluating and retrieving project industry domains.
 *
 * Other backend modules should depend on this interface instead of calling
 * user-module services directly.
 */
interface ProjectIndustryApi {
    /**
     * Evaluates the industry domain for a project via AI and always persists the result,
     * overriding any existing industry and confidence values.
     *
     * @param projectId Unique identifier of the project.
     * @return The AI evaluation response.
     */
    suspend fun evaluateIndustry(projectId: UUID): AiIndustryEvaluationResponse

    /**
     * Returns the persisted industry for a project if available. If unset, queries the AI
     * service once and persists the result only if the confidence is at least `medium`.
     *
     * @param projectId Unique identifier of the project.
     * @return The detected industry name, or `null` if unset/low-confidence/unavailable.
     */
    suspend fun getOrEvaluateIndustry(projectId: UUID): String?

    /**
     * Evaluates the project industry automatically (e.g. after an ingestion run) and persists
     * it only if confidence is at least `medium` and strictly higher than the current confidence.
     *
     * Errors during evaluation are logged and not propagated.
     *
     * @param projectId Unique identifier of the project.
     */
    suspend fun evaluateIndustryAutomatically(projectId: UUID)
}
