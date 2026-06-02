package com.sprintstart.sprintstartbackend.onboarding.model.request.phase

data class CreateOnboardingPhaseRequest(
    val position: Int,
    val title: String,
    val description: String,
)
