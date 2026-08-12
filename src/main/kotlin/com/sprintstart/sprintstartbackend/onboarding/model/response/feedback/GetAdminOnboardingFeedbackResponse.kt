package com.sprintstart.sprintstartbackend.onboarding.model.response.feedback

import java.time.Instant
import java.util.UUID

data class GetAdminOnboardingFeedbackResponse(
    val id: UUID,
    val userId: UUID,
    val pageId: UUID?,
    val pageTitle: String?,
    // Which module the page belongs to, and what it teaches -- feedback on a shared page is a
    // signal about that competency's material, so the reviewer needs to see which one.
    val moduleId: UUID?,
    val competencyKey: String?,
    val message: String,
    val read: Boolean,
    val createdAt: Instant,
)
