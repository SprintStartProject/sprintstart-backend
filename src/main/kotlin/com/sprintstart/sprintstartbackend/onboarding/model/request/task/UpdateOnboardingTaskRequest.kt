package com.sprintstart.sprintstartbackend.onboarding.model.request.task

data class UpdateOnboardingTaskRequest(
    val position: Int,
    val title: String,
    val description: String,
    val finished: Boolean,
)
