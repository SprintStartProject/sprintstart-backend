package com.sprintstart.sprintstartbackend.onboarding.model.response.path

import io.swagger.v3.oas.annotations.media.Schema

/** Counts actionable items, so a member with both a skip request and unread feedback contributes to both. */
data class ProjectPmActionsResponse(
    @field:Schema(description = "Pending skip requests across all steps of project members' onboarding paths")
    val pendingSkipRequests: Long,
    @field:Schema(description = "Unread onboarding feedback from project members, including feedback without a step")
    val unreadFeedback: Long,
) {
    @get:Schema(description = "Total actionable items: pendingSkipRequests + unreadFeedback")
    val total: Long
        get() = pendingSkipRequests + unreadFeedback
}
