package com.sprintstart.sprintstartbackend.path.model.request.resource

data class CreateOnboardingResourceRequest(
    val title: String,
    val description: String,
    val url: String,
)
