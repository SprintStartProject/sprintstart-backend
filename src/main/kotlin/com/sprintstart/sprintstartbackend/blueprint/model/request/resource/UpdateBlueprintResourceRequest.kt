package com.sprintstart.sprintstartbackend.blueprint.model.request.resource

data class UpdateBlueprintResourceRequest(
    val revision: Long,
    val title: String,
    val description: String,
    val url: String,
)
