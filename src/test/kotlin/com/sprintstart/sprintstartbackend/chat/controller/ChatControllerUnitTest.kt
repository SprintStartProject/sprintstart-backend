package com.sprintstart.sprintstartbackend.chat.controller

import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.PromptRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.CreateChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatMessagesResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatsResponse
import com.sprintstart.sprintstartbackend.chat.service.ChatService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Pure unit tests for [ChatController].
 *
 * No Spring context — instantiate the controller directly and verify it delegates
 * correctly to [ChatService] and returns whatever the service produces unchanged.
 *
 * Validation (@Valid) and HTTP concerns (status codes, routing, serialization) are
 * covered by [ChatControllerWebMvcTest].
 */
class ChatControllerUnitTest {
    private val chatService: ChatService = mockk()
    private val controller = ChatController(chatService)

    private val chatId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private val sampleChatResponse = ChatResponse(
        id = chatId,
        title = "Sprint planning",
        userId = userId,
        createdAt = OffsetDateTime.now(),
    )

    @Nested
    inner class GetChats {
        @Test
        fun `delegates to service and returns response unchanged`() {
            val request = GetChatsRequest(limit = 10)
            val expected = GetChatsResponse(chats = listOf(sampleChatResponse))
            every { chatService.getChats(request) } returns expected

            val result = controller.getChats(request)

            assertEquals(expected, result)
            verify(exactly = 1) { chatService.getChats(request) }
        }

        @Test
        fun `passes null limit to service as-is`() {
            val request = GetChatsRequest(limit = null)
            val expected = GetChatsResponse(chats = emptyList())
            every { chatService.getChats(request) } returns expected

            val result = controller.getChats(request)

            assertEquals(expected, result)
            verify { chatService.getChats(request) }
        }
    }

    @Nested
    inner class GetChatMessages {
        @Test
        fun `delegates to service with correct id and request, returns response unchanged`() {
            val request = GetChatMessagesRequest(limit = 5)
            val expected = GetChatMessagesResponse(
                messages = listOf(ChatMessageResponse(role = ChatRole.USER, content = "Hello")),
            )
            every { chatService.getChat(chatId, request) } returns expected

            val result = controller.getChatMessages(chatId, request)

            assertEquals(expected, result)
            verify(exactly = 1) { chatService.getChat(chatId, request) }
        }

        @Test
        fun `passes null limit to service as-is`() {
            val request = GetChatMessagesRequest(limit = null)
            val expected = GetChatMessagesResponse(messages = emptyList())
            every { chatService.getChat(chatId, request) } returns expected

            val result = controller.getChatMessages(chatId, request)

            assertEquals(expected, result)
            verify { chatService.getChat(chatId, request) }
        }
    }

    @Nested
    inner class CreateChat {
        @Test
        fun `delegates to service and returns new chat id unchanged`() {
            val request = CreateChatRequest(userId = userId)
            val expected = CreateChatResponse(id = chatId)
            every { chatService.createChat(request) } returns expected

            val result = controller.createChat(request)

            assertEquals(expected, result)
            verify(exactly = 1) { chatService.createChat(request) }
        }
    }

    @Nested
    inner class Prompt {
        @Test
        fun `delegates to service and returns flow unchanged`() = runTest {
            val request = PromptRequest(chatId = chatId, msg = "What is the sprint goal?")
            val tokens = listOf(
                """{"type":"token","content":"The"}""",
                """{"type":"token","content":" goal"}""",
                """{"type":"done"}""",
            )
            every { chatService.prompt(request) } returns flowOf(*tokens.toTypedArray())

            val result = controller.prompt(request).toList()

            assertEquals(tokens, result)
            verify(exactly = 1) { chatService.prompt(request) }
        }
    }
}
