package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task

data class CreateBlueprintTaskRequest(
    val position: Int,
    val title: String,
    val description: String,
)
