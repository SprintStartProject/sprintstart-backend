package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Components with missing documentation for the PM knowledge-gaps panel.")
data class KnowledgeGapsOverviewResponse(
    @field:Schema(description = "Knowledge gaps ordered by descending severity, then related-question count.")
    val gaps: List<KnowledgeGapResponse>,
    @field:Schema(
        description = "True while a rescan triggered by newly ingested documentation is still " +
            "running. The gaps returned alongside it are the previous result.",
    )
    val refreshing: Boolean = false,
    @field:Schema(description = "When the returned gaps were computed. Null when none have been computed yet.")
    val refreshedAt: Instant? = null,
)
