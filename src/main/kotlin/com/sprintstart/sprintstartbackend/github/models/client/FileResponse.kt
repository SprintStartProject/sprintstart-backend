package com.sprintstart.sprintstartbackend.github.models.client

import kotlinx.serialization.Serializable

@Serializable
data class FileResponse(
    val path: String,
    val sha: String,
    val type: String,
    val content: String,
    val encoding: String,
)
