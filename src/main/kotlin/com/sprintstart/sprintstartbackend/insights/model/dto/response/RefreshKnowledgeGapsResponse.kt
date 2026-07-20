package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Outcome of a knowledge-gap classification refresh.")
data class RefreshKnowledgeGapsResponse(
    @field:Schema(description = "Number of knowledge gaps stored after the refresh.")
    val gapCount: Int,
)
