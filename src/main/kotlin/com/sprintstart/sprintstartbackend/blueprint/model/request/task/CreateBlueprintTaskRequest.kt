package com.sprintstart.sprintstartbackend.blueprint.model.request.task

data class CreateBlueprintTaskRequest(
    val position: Int,
    val title: String,
    val description: String,
)
