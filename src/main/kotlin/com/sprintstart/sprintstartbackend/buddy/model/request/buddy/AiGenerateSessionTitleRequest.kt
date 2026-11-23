package com.sprintstart.sprintstartbackend.buddy.model.request.buddy

import kotlinx.serialization.Serializable

@Serializable
data class AiGenerateSessionTitleRequest(
    val prompt: String,
)
