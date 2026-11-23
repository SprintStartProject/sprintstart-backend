package com.sprintstart.sprintstartbackend.path.model.request.phase

data class UpdateOnboardingPhaseRequest(
    val position: Int,
    val title: String,
    val description: String,
)
