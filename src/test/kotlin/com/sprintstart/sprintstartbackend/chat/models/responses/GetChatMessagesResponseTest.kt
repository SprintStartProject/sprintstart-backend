package com.sprintstart.sprintstartbackend.chat.models.responses

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class GetChatMessagesResponseTest {
    @Test
    fun `conversion to response succeeds`() {
        val chat = Chat(UUID.randomUUID(), "Chat title", UUID.randomUUID(), OffsetDateTime.now())
        val chatMessage = ChatMessage(UUID.randomUUID(), ChatRole.USER, chat, "Some content", OffsetDateTime.now())

        val request = chatMessage.toChatMessageResponse()

        assertEquals(chatMessage.role, request.role)
        assertEquals(chatMessage.content, request.content)
    }
}
