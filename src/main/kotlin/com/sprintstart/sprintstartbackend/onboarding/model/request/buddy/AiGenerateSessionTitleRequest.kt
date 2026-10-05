package com.sprintstart.sprintstartbackend.onboarding.model.request.buddy

import kotlinx.serialization.Serializable

@Serializable
data class AiGenerateSessionTitleRequest(
    val prompt: String,
)
