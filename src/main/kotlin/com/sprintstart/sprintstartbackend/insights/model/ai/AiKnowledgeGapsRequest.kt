package com.sprintstart.sprintstartbackend.insights.model.ai

import kotlinx.serialization.Serializable

/**
 * Request sent to the AI service to (re)classify knowledge gaps.
 *
 * The AI service owns the ingested artifacts and performs the missing-runbook/ADR detection, so the
 * backend does not send any source data. [limit] optionally caps how many gaps the service should
 * return; `null` requests all of them.
 */
@Serializable
data class AiKnowledgeGapsRequest(
    val limit: Int? = null,
)
