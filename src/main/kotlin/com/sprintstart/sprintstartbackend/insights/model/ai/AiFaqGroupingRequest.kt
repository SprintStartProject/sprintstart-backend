package com.sprintstart.sprintstartbackend.insights.model.ai

import kotlinx.serialization.Serializable

/**
 * Request sent to the AI service to (re)compute recurring-question groups.
 *
 * The AI service is stateless and does not retain question history, so the backend supplies the
 * full set of [questions] to group on every request. The service performs the semantic clustering
 * and PII redaction.
 */
@Serializable
data class AiFaqGroupingRequest(
    val projectId: String,
    val questions: List<AiFaqQuestion>,
    // Passed rather than left to the AI service's own default, so a rebuild produces the same
    // category budget the live classification path enforces.
    val maxCategories: Int,
)

/**
 * A single question handed to the AI service for grouping.
 *
 * @property id backend-assigned identifier of the question
 * @property text the raw question text
 */
@Serializable
data class AiFaqQuestion(
    val id: String,
    val text: String,
)
