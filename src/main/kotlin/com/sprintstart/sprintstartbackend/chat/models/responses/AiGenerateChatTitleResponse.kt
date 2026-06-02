package com.sprintstart.sprintstartbackend.chat.models.responses

import kotlinx.serialization.Serializable

/**
 * The response of the AI repo including a new chat's title.
 *
 * @property title The new title of the chat.
 */
@Serializable
data class AiGenerateChatTitleResponse(
    val title: String,
)
