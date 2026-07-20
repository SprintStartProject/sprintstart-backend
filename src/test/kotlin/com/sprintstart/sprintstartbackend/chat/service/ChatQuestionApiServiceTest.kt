package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class ChatQuestionApiServiceTest {
    private val messageRepository = mockk<ChatMessageRepository>()
    private val service = ChatQuestionApiService(messageRepository)

    @Test
    fun `getAllUserQuestions maps user messages to questions`() {
        val chat = Chat(userId = UUID.randomUUID(), createdAt = OffsetDateTime.now())
        val message = ChatMessage(
            role = ChatRole.USER,
            chat = chat,
            content = "How do I get VPN access?",
            createdAt = OffsetDateTime.now(),
        )
        every { messageRepository.findAllByRole(ChatRole.USER) } returns listOf(message)

        val result = service.getAllUserQuestions()

        assertEquals(1, result.size)
        assertEquals(message.id, result.first().id)
        assertEquals("How do I get VPN access?", result.first().text)
        verify(exactly = 1) { messageRepository.findAllByRole(ChatRole.USER) }
    }
}
