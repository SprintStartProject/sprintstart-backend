package com.sprintstart.sprintstartbackend.chat.models.responses

import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole

/**
 * The response to send out to the frontend including the messages of a specific chat.
 *
 * @property messages The messages of the chat.
 */
internal data class GetChatMessagesResponse(
    val messages: List<ChatMessageResponse>,
)

internal data class ChatMessageResponse(
    val role: ChatRole,
    val content: String,
)

internal fun ChatMessage.toChatMessageResponse(): ChatMessageResponse {
    return ChatMessageResponse(
        role = this.role,
        content = this.content,
    )
}
