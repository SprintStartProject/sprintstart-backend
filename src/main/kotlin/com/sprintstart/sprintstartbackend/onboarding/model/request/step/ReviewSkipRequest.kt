package com.sprintstart.sprintstartbackend.onboarding.model.request.step

data class ReviewSkipRequest(
    val approved: Boolean,
    val reviewComment: String?,
)
