package com.sprintstart.sprintstartbackend.path.model.request.phase

data class CreateOnboardingPhaseRequest(
    val position: Int,
    val title: String,
    val description: String,
)
