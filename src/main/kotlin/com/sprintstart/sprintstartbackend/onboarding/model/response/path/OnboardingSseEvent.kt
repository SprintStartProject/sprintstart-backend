package com.sprintstart.sprintstartbackend.onboarding.model.response.path

data class OnboardingSseEvent(
    val type: String,
    val name: String? = null,
    val detail: String? = null,
    val path: GetOnboardingPathForUserResponse? = null,
    val message: String? = null,
)
