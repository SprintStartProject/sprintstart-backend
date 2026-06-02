package com.sprintstart.sprintstartbackend.chat.models.responses

import com.sprintstart.sprintstartbackend.chat.models.Chat
import java.time.OffsetDateTime
import java.util.UUID

/**
 * The response to send out to the frontend including the chats that were queried and their metadata.
 *
 * Please note that this function only includes chat metadata, not messages!
 *
 * @property chats The chats including metadata.
 */
internal data class GetChatsResponse(
    val chats: List<ChatResponse>,
)

internal data class ChatResponse(
    val id: UUID,
    val title: String,
    val userId: UUID,
    val createdAt: OffsetDateTime,
)

internal fun Chat.toChatResponse(): ChatResponse {
    return ChatResponse(
        id = this.id,
        title = this.title,
        userId = this.userId,
        createdAt = this.createdAt,
    )
}
