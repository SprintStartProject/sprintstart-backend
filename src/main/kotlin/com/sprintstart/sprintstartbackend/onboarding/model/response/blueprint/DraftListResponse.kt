package com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint

data class DraftListResponse(
    val items: List<DraftSummaryResponse>,
)

data class DraftSummaryResponse(
    val scope: String,
    val createdAt: String? = null,
    val summary: String? = null,
)
