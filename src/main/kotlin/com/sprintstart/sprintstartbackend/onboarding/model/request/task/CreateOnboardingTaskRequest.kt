package com.sprintstart.sprintstartbackend.onboarding.model.request.task

data class CreateOnboardingTaskRequest(
    val position: Int,
    val title: String,
    val description: String,
)
