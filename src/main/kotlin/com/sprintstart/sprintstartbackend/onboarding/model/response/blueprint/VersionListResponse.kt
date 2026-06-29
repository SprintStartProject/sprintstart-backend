package com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint

data class VersionListResponse(
    val scope: String,
    val versions: List<String>,
)
