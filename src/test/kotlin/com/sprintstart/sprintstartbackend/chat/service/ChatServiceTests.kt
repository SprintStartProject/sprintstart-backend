package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.chat.AiWebClientImpl
import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.requests.AiPromptRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.PromptRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.AiChatTitleResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatResponse
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.client.HttpClientErrorException
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class ChatServiceTests {
    private val applicationConfig: ApplicationConfig = mockk()
    private val chatRepository: ChatRepository = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk()
    private val webRequestClient: AiWebClientImpl = mockk()
    private val userApi: UserApi = mockk()
    private val chatService = ChatService(
        applicationConfig,
        chatRepository,
        chatMessageRepository,
        webRequestClient,
        userApi,
    )

    private val userId = UUID.randomUUID()

    @Nested
    inner class GetChats {
        private val allChats = listOf(
            Chat(UUID.randomUUID(), "First", userId, OffsetDateTime.now()),
            Chat(UUID.randomUUID(), "Second", userId, OffsetDateTime.now()),
            Chat(UUID.randomUUID(), "Third", userId, OffsetDateTime.now()),
            Chat(UUID.randomUUID(), "Fourth", userId, OffsetDateTime.now()),
            Chat(UUID.randomUUID(), "Fifth", userId, OffsetDateTime.now()),
        )

        @Test
        fun `returns all chats on null limit`() {
            val request = GetChatsRequest(limit = null)
            every { chatRepository.findAll(any<Pageable>()) } returns PageImpl(allChats)

            val result = chatService.getChats(request)

            assertEquals(5, result.chats.size)
            assertEquals(allChats[0].toChatResponse(), result.chats[0])
            assertEquals(allChats[1].toChatResponse(), result.chats[1])
            assertEquals(allChats[2].toChatResponse(), result.chats[2])
            assertEquals(allChats[3].toChatResponse(), result.chats[3])
            assertEquals(allChats[4].toChatResponse(), result.chats[4])
        }

        @Test
        fun `returns all chats on large enough limit`() {
            val request = GetChatsRequest(limit = 5)
            every { chatRepository.findAll(any<Pageable>()) } returns PageImpl(allChats)

            val result = chatService.getChats(request)

            assertEquals(5, result.chats.size)
            assertEquals(allChats[0].toChatResponse(), result.chats[0])
            assertEquals(allChats[1].toChatResponse(), result.chats[1])
            assertEquals(allChats[2].toChatResponse(), result.chats[2])
            assertEquals(allChats[3].toChatResponse(), result.chats[3])
            assertEquals(allChats[4].toChatResponse(), result.chats[4])
        }

        @Test
        fun `returns all chats on too large limit`() {
            val request = GetChatsRequest(limit = 10)
            every { chatRepository.findAll(any<Pageable>()) } returns PageImpl(allChats)

            val result = chatService.getChats(request)

            assertEquals(5, result.chats.size)
            assertEquals(allChats[0].toChatResponse(), result.chats[0])
            assertEquals(allChats[1].toChatResponse(), result.chats[1])
            assertEquals(allChats[2].toChatResponse(), result.chats[2])
            assertEquals(allChats[3].toChatResponse(), result.chats[3])
            assertEquals(allChats[4].toChatResponse(), result.chats[4])
        }

        @Test
        fun `returns only n chats for limit n`() {
            val request = GetChatsRequest(limit = 3)
            every { chatRepository.findAll(any<Pageable>()) } returns PageImpl(
                listOf(
                    allChats[0],
                    allChats[1],
                    allChats[2],
                ),
            )

            val result = chatService.getChats(request)

            assertEquals(3, result.chats.size)
            assertEquals(allChats[0].toChatResponse(), result.chats[0])
            assertEquals(allChats[1].toChatResponse(), result.chats[1])
            assertEquals(allChats[2].toChatResponse(), result.chats[2])
        }
    }

    @Nested
    inner class GetChat {
        private val chat = Chat(UUID.randomUUID(), "Some test chat", userId, OffsetDateTime.now())
        private val chatMessages = listOf(
            ChatMessage(UUID.randomUUID(), ChatRole.USER, chat, "First message", OffsetDateTime.now()),
            ChatMessage(UUID.randomUUID(), ChatRole.USER, chat, "Second message", OffsetDateTime.now()),
            ChatMessage(UUID.randomUUID(), ChatRole.USER, chat, "Third message", OffsetDateTime.now()),
            ChatMessage(UUID.randomUUID(), ChatRole.USER, chat, "Fourth message", OffsetDateTime.now()),
            ChatMessage(UUID.randomUUID(), ChatRole.USER, chat, "Fifth message", OffsetDateTime.now()),
        )

        @Test
        fun `returns chat with all messages on null limit`() {
            val request = GetChatMessagesRequest(limit = null)
            every {
                chatMessageRepository.findAllByChat(any<UUID>(), any<Pageable>())
            } returns PageImpl(chatMessages)

            val result = chatService.getChat(chat.id, request)

            assertEquals(5, result.messages.size)
            assertEquals(chatMessages[0].toChatMessageResponse(), result.messages[0])
            assertEquals(chatMessages[1].toChatMessageResponse(), result.messages[1])
            assertEquals(chatMessages[2].toChatMessageResponse(), result.messages[2])
            assertEquals(chatMessages[3].toChatMessageResponse(), result.messages[3])
            assertEquals(chatMessages[4].toChatMessageResponse(), result.messages[4])
        }

        @Test
        fun `returns chat with all messages on large enough limit`() {
            val request = GetChatMessagesRequest(limit = 5)
            every {
                chatMessageRepository.findAllByChat(any<UUID>(), any<Pageable>())
            } returns PageImpl(chatMessages)

            val result = chatService.getChat(chat.id, request)

            assertEquals(5, result.messages.size)
            assertEquals(chatMessages[0].toChatMessageResponse(), result.messages[0])
            assertEquals(chatMessages[1].toChatMessageResponse(), result.messages[1])
            assertEquals(chatMessages[2].toChatMessageResponse(), result.messages[2])
            assertEquals(chatMessages[3].toChatMessageResponse(), result.messages[3])
            assertEquals(chatMessages[4].toChatMessageResponse(), result.messages[4])
        }

        @Test
        fun `returns chat with all messages on too enough limit`() {
            val request = GetChatMessagesRequest(limit = 10)
            every {
                chatMessageRepository.findAllByChat(any<UUID>(), any<Pageable>())
            } returns PageImpl(chatMessages)

            val result = chatService.getChat(chat.id, request)

            assertEquals(5, result.messages.size)
            assertEquals(chatMessages[0].toChatMessageResponse(), result.messages[0])
            assertEquals(chatMessages[1].toChatMessageResponse(), result.messages[1])
            assertEquals(chatMessages[2].toChatMessageResponse(), result.messages[2])
            assertEquals(chatMessages[3].toChatMessageResponse(), result.messages[3])
            assertEquals(chatMessages[4].toChatMessageResponse(), result.messages[4])
        }

        @Test
        fun `returns n chat messages on limit n`() {
            val request = GetChatMessagesRequest(limit = 3)
            every {
                chatMessageRepository.findAllByChat(any<UUID>(), any<Pageable>())
            } returns PageImpl(listOf(chatMessages[0], chatMessages[1], chatMessages[2]))

            val result = chatService.getChat(chat.id, request)

            assertEquals(3, result.messages.size)
            assertEquals(chatMessages[0].toChatMessageResponse(), result.messages[0])
            assertEquals(chatMessages[1].toChatMessageResponse(), result.messages[1])
            assertEquals(chatMessages[2].toChatMessageResponse(), result.messages[2])
        }
    }

    @Nested
    inner class CreateChat {
        @Test
        fun `creates chat with correct userId and returns its id`() {
            val request = CreateChatRequest(userId = userId)
            val chatSlot = slot<Chat>()
            every { chatRepository.save(capture(chatSlot)) } answers { chatSlot.captured }
            every { userApi.exists(any()) } returns true

            val result = chatService.createChat(request)

            assertEquals(userId, chatSlot.captured.userId)
            assertEquals(chatSlot.captured.id, result.id)
            verify(exactly = 1) { chatRepository.save(any()) }
        }

        @Test
        fun `throws exception when creating chat with incorrect userId`() {
            val request = CreateChatRequest(userId = userId)
            val chatSlot = slot<Chat>()
            every { chatRepository.save(capture(chatSlot)) } answers { chatSlot.captured }
            every { userApi.exists(any()) } returns false

            assertThrows<HttpClientErrorException> { chatService.createChat(request) }

            verify(exactly = 0) { chatRepository.save(any()) }
        }
    }

    @Nested
    inner class PromptAi {
        private val chatId = UUID.randomUUID()

        @Test
        fun `emits tokens from ai stream`() = runTest {
            val chat = Chat(id = chatId, userId = userId, title = "Existing title", createdAt = OffsetDateTime.now())
            val aiPromptRequest = AiPromptRequest("Hello", listOf())
            val tokens = listOf(
                """{"type":"token","content":"Hello"}""",
                """{"type":"token","content":" world"}""",
                """{"type":"done"}""",
            )
            every { chatRepository.findById(chatId) } returns Optional.of(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { webRequestClient.streamPrompt(any(), aiPromptRequest) } returns flowOf(*tokens.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            val result = chatService.prompt(PromptRequest(chatId = chatId, msg = "Hello")).toList()

            assertEquals(tokens, result)
        }

        @Test
        fun `saves ai response as message on stream completion`() = runTest {
            val chat = Chat(id = chatId, userId = userId, title = "Existing title", createdAt = OffsetDateTime.now())
            val aiPromptRequest = AiPromptRequest("Hello", listOf())
            val tokens = listOf(
                """{"type":"token","content":"Hello"}""",
                """{"type":"token","content":" world"}""",
            )
            val savedMessages = mutableListOf<ChatMessage>()
            every { chatRepository.findById(chatId) } returns Optional.of(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(capture(savedMessages)) } answers { firstArg() }
            every { webRequestClient.streamPrompt(any(), aiPromptRequest) } returns flowOf(*tokens.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"
            every { webRequestClient.jsonParser } returns Json { ignoreUnknownKeys = true }

            chatService.prompt(PromptRequest(chatId = chatId, msg = "Hello")).toList() // collect to trigger completion

            // First save = user message, second save = AI response
            assertEquals(2, savedMessages.size)
            assertEquals(ChatRole.AI, savedMessages[1].role)
            assertEquals("Hello world", savedMessages[1].content)
        }

        @Test
        fun `generates and saves title when chat title is blank`() = runTest {
            val chat = Chat(id = chatId, userId = userId, title = "", createdAt = OffsetDateTime.now())
            val aiPromptRequest = AiPromptRequest("Hello", listOf())
            every { chatRepository.findById(chatId) } returns Optional.of(chat)
            every { chatRepository.save(any()) } answers { firstArg() }
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { webRequestClient.getChatTitle(any()) } returns AiChatTitleResponse("Sprint planning")
            every { webRequestClient.streamPrompt(any(), aiPromptRequest) } returns flowOf()
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            chatService.prompt(PromptRequest(chatId = chatId, msg = "Hello")).toList()

            assertEquals("Sprint planning", chat.title)
            verify { chatRepository.save(chat) }
        }

        @Test
        fun `skips title generation when chat title is not blank`() = runTest {
            val chat = Chat(id = chatId, userId = userId, title = "Existing", createdAt = OffsetDateTime.now())
            every { chatRepository.findById(chatId) } returns Optional.of(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { webRequestClient.streamPrompt(any(), any()) } returns flowOf()
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            chatService.prompt(PromptRequest(chatId = chatId, msg = "Hello")).toList()

            verify(exactly = 0) { webRequestClient.getChatTitle(any()) }
            verify(exactly = 0) { chatRepository.save(any()) }
        }

        @Test
        fun `throws when chat is not found`() {
            every { chatRepository.findById(chatId) } returns Optional.empty()

            assertThrows<HttpClientErrorException> {
                chatService.prompt(PromptRequest(chatId = chatId, msg = "Hello"))
            }
        }

        @Test
        fun `does not save ai response when stream errors`() = runTest {
            val chat = Chat(id = chatId, userId = userId, title = "Existing", createdAt = OffsetDateTime.now())
            every { chatRepository.findById(chatId) } returns Optional.of(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { webRequestClient.streamPrompt(any(), any()) } returns flow {
                emit("""{"type":"token","content":"Hello"}""")
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException("AI backend unreachable")
            }

            // Collect and ignore the error — we're testing the side effect (no AI message saved)
            runCatching {
                chatService.prompt(PromptRequest(chatId = chatId, msg = "Hello")).toList()
            }

            // Only the user message should have been saved, not the AI response
            verify(exactly = 1) { chatMessageRepository.save(any()) }
        }
    }
}
