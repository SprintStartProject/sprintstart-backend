package com.sprintstart.sprintstartbackend.onboarding.model.request.resource

data class UpdateOnboardingResourceRequest(
    val title: String,
    val description: String,
    val url: String,
)
