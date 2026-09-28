package com.sprintstart.sprintstartbackend.user.external.model

import kotlinx.serialization.Serializable

/**
 * Request payload sent to the AI service to evaluate a project's industry domain.
 *
 * @property projectId The project to evaluate, scoping the retrieval.
 */
@Serializable
data class AiIndustryEvaluationRequest(
    val projectId: String,
)
