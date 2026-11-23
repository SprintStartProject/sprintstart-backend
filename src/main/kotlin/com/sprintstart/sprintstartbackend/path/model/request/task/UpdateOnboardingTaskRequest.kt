package com.sprintstart.sprintstartbackend.path.model.request.task

data class UpdateOnboardingTaskRequest(
    val position: Int,
    val title: String,
    val description: String,
    val finished: Boolean,
)
