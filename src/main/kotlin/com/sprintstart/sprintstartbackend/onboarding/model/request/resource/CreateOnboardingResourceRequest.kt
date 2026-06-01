package com.sprintstart.sprintstartbackend.onboarding.model.request.resource

data class CreateOnboardingResourceRequest(
    val title: String,
    val description: String,
    val url: String,
)
