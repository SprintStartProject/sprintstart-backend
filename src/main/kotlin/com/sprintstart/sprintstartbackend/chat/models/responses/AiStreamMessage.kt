package com.sprintstart.sprintstartbackend.chat.models.responses

import kotlinx.serialization.Serializable

/**
 * Specifies the format of streamed messages incoming from the AI repo.
 *
 * @property type The type of stream message this is (e.g. 'token', 'done', 'error', ...).
 * @property content The content of the stream message.
 */
@Serializable
data class AiStreamMessage(
    val type: String,
    val content: String? = null,
)
