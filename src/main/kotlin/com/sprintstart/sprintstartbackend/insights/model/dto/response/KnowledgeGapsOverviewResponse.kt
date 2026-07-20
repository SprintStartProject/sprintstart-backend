package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Components with missing documentation for the PM knowledge-gaps panel.")
data class KnowledgeGapsOverviewResponse(
    @field:Schema(description = "Knowledge gaps ordered by descending severity, then related-question count.")
    val gaps: List<KnowledgeGapResponse>,
)
