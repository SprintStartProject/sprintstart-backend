package com.sprintstart.sprintstartbackend.chat.models.responses

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.Citation
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class GetChatMessagesResponseTest {
    @Test
    fun `conversion to response succeeds`() {
        val chat = Chat(
            UUID.randomUUID(),
            "Chat title",
            UUID.randomUUID(),
            OffsetDateTime.now(),
        )
        val chatMessage = ChatMessage(
            UUID.randomUUID(),
            ChatRole.USER,
            chat,
            emptyList(),
            "Some content",
            OffsetDateTime.now(),
        )
        val citation = Citation(
            UUID.randomUUID(),
            chatMessage,
            UUID.randomUUID(),
            "document.pdf",
            "https://example.com/document.pdf",
            null,
            3,
        )
        chatMessage.citations = listOf(citation)

        val request = chatMessage.toChatMessageResponse()

        assertEquals(chatMessage.role, request.role)
        assertEquals(chatMessage.content, request.content)

        assertEquals(1, request.citations.size)
        assertEquals(citation.id, request.citations[0].id)
        assertEquals(citation.artifactId, request.citations[0].artifactId)
        assertEquals(citation.filename, request.citations[0].filename)
        assertEquals(citation.sourceUrl, request.citations[0].sourceUrl)
        assertEquals(citation.startLine, request.citations[0].startLine)
        assertEquals(citation.startPage, request.citations[0].startPage)
    }
}
