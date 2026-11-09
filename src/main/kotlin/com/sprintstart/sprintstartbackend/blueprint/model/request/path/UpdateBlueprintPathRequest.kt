package com.sprintstart.sprintstartbackend.blueprint.model.request.path

data class UpdateBlueprintPathRequest(
    val title: String,
    val description: String,
    val version: Int,
    val revision: Long,
)
