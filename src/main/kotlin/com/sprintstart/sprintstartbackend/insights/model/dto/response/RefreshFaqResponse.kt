package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Outcome of a FAQ grouping refresh.")
data class RefreshFaqResponse(
    @field:Schema(description = "Number of recurring-question groups stored after the refresh.")
    val groupCount: Int,
)
