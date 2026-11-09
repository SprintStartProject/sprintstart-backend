package com.sprintstart.sprintstartbackend.path.model.response.feedback

import java.util.UUID

data class ReadOnboardingFeedbackResponse(
    val id: UUID,
    val read: Boolean,
)
