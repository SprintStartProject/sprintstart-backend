package com.sprintstart.sprintstartbackend.insights.model.ai

import kotlinx.serialization.Serializable

/**
 * Knowledge gaps returned by the AI service.
 */
@Serializable
data class AiKnowledgeGapsResponse(
    val gaps: List<AiKnowledgeGap>,
)

/**
 * A single component that is missing critical documentation.
 *
 * The AI service intentionally does not return owners or a related-question count: its ingestion
 * index holds no user/ownership data and it retains no question history. Ownership is enriched by
 * the backend.
 *
 * @property component name of the component
 * @property missingTypes document types the component is missing, for example "runbook" or "adr";
 * empty when the component is fully covered
 * @property presentTypes document types the component already has
 * @property lastUpdated ISO-8601 timestamp of the component's most recent activity
 * @property severity impact level, one of "high", "medium", "low" or "covered". The AI service
 * reports every component of the project, so "covered" marks one that is missing nothing rather
 * than a gap.
 */
@Serializable
data class AiKnowledgeGap(
    val component: String,
    val missingTypes: List<String>,
    val presentTypes: List<String>,
    val lastUpdated: String,
    val severity: String,
)
