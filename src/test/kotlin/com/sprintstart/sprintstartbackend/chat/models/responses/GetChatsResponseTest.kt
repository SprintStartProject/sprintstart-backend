package com.sprintstart.sprintstartbackend.chat.models.responses

import com.sprintstart.sprintstartbackend.chat.models.Chat
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class GetChatsResponseTest {
    @Test
    fun `conversion to response succeeds`() {
        val chat = Chat(UUID.randomUUID(), "Chat title", UUID.randomUUID(), OffsetDateTime.now())

        val request = chat.toChatResponse()

        assertEquals(chat.id, request.id)
        assertEquals(chat.title, request.title)
        assertEquals(chat.userId, request.userId)
        assertEquals(chat.createdAt, request.createdAt)
    }
}
