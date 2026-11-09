package com.sprintstart.sprintstartbackend.blueprint.model.request.task

data class UpdateBlueprintTaskRequest(
    val revision: Long,
    val position: Int,
    val title: String,
    val description: String,
)
