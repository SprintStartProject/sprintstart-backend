package com.sprintstart.sprintstartbackend.blueprint.model.request.resource

data class CreateBlueprintResourceRequest(
    val title: String,
    val description: String,
    val url: String,
)
