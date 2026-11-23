package com.sprintstart.sprintstartbackend.path.model.request.task

data class CreateOnboardingTaskRequest(
    val position: Int,
    val title: String,
    val description: String,
)
