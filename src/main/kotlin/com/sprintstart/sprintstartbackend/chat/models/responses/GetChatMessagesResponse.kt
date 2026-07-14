package com.sprintstart.sprintstartbackend.chat.models.responses

import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.Citation
import java.util.UUID

/**
 * The response to send out to the frontend including the messages of a specific chat.
 *
 * @property messages The messages of the chat.
 */
internal data class GetChatMessagesResponse(
    val messages: List<ChatMessageResponse>,
)

internal data class CitationResponse(
    val id: UUID,
    val artifactId: UUID,
    val filename: String,
    val sourceUrl: String?,
    val startLine: Int?,
    val startPage: Int?,
)

internal data class ChatMessageResponse(
    val role: ChatRole,
    val content: String,
    val citations: List<CitationResponse> = emptyList(),
)

internal fun Citation.toResponse() =
    CitationResponse(
        id = id,
        artifactId = artifactId,
        filename = filename,
        sourceUrl = sourceUrl,
        startLine = startLine,
        startPage = startPage,
    )

internal fun ChatMessage.toChatMessageResponse(): ChatMessageResponse {
    return ChatMessageResponse(
        role = this.role,
        content = this.content,
        citations = citations.map { it.toResponse() },
    )
}
