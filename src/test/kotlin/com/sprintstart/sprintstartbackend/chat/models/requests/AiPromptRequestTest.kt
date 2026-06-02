package com.sprintstart.sprintstartbackend.chat.models.requests

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals

class AiPromptRequestTest {
    @Test
    fun `conversion to request succeeds`() {
        val chat = Chat(UUID.randomUUID(), "Chat title", UUID.randomUUID(), OffsetDateTime.now())
        val chatMessage = ChatMessage(UUID.randomUUID(), ChatRole.USER, chat, "Some content", OffsetDateTime.now())

        val request = chatMessage.toAiContextEntry()

        assertEquals(chatMessage.role.name.lowercase(), request.role)
        assertEquals(chatMessage.content, request.content)
    }
}
