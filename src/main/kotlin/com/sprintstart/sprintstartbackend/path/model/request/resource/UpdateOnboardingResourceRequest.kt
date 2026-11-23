package com.sprintstart.sprintstartbackend.path.model.request.resource

data class UpdateOnboardingResourceRequest(
    val title: String,
    val description: String,
    val url: String,
)
