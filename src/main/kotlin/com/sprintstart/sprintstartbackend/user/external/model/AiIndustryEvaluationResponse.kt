package com.sprintstart.sprintstartbackend.user.external.model

import kotlinx.serialization.Serializable

/**
 * Result returned by the AI service after evaluating a project's industry from its corpus.
 *
 * @property industry Detected industry/domain name, or empty string if undetermined.
 * @property confidence Estimated confidence level ("high", "medium", "low").
 * @property evidence Key evidence phrases or artifact filenames grounding the evaluation.
 */
@Serializable
data class AiIndustryEvaluationResponse(
    val industry: String,
    val confidence: String,
    val evidence: List<String> = emptyList(),
)
