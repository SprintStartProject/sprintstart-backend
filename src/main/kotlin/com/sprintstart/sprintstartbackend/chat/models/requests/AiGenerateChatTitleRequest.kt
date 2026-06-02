package com.sprintstart.sprintstartbackend.chat.models.requests

import kotlinx.serialization.Serializable

/**
 * Format for requesting title generation from the AI.
 */
@Serializable
data class AiGenerateChatTitleRequest(
    val prompt: String,
)
