package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Outcome of a knowledge-gap classification refresh.")
data class RefreshKnowledgeGapsResponse(
    @field:Schema(
        description = "Number of components with at least one missing document type. Counts " +
            "actual gaps only, so a scan that found nothing wrong reports zero even though it " +
            "stored a row per component.",
    )
    val gapCount: Int,
    @field:Schema(
        description = "Number of components the scan covered, including the ones missing nothing.",
    )
    val componentCount: Int = 0,
)
