package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.chat.ChatAiClient
import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatFilters
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.Citation
import com.sprintstart.sprintstartbackend.chat.models.requests.AiPromptRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.PromptRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.AiGenerateChatTitleResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatResponse
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.chat.repository.CitationRepository
import com.sprintstart.sprintstartbackend.connectors.overview.external.models.ConnectorDto
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.ConnectorDisabledException
import com.sprintstart.sprintstartbackend.connectors.overview.service.ConnectorConfigurationService
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatServiceTests {
    private val applicationConfig: ApplicationConfig = mockk()
    private val chatRepository: ChatRepository = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk()
    private val citationRepository: CitationRepository = mockk()
    private val connectorConfigurationService: ConnectorConfigurationService = mockk()
    private val chatAiClient: ChatAiClient = mockk()
    private val userApi: UserApi = mockk()
    private val artifactLookupService: ArtifactLookupService = mockk()

    // Relaxed: the question-asked event is fire-and-forget analytics, and no test here is about
    // what listens to it.
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val chatService = ChatService(
        chatRepository,
        chatMessageRepository,
        citationRepository,
        connectorConfigurationService,
        chatAiClient,
        userApi,
        artifactLookupService,
        eventPublisher,
    )

    private val userId = UUID.randomUUID()
    private val authId = "auth-user"
    private val projectId = UUID.randomUUID()

    @Nested
    inner class GetChats {
        private val allChats = listOf(
            Chat(UUID.randomUUID(), "First", userId, OffsetDateTime.now(), projectId),
            Chat(UUID.randomUUID(), "Second", userId, OffsetDateTime.now(), projectId),
            Chat(UUID.randomUUID(), "Third", userId, OffsetDateTime.now(), projectId),
            Chat(UUID.randomUUID(), "Fourth", userId, OffsetDateTime.now(), projectId),
            Chat(UUID.randomUUID(), "Fifth", userId, OffsetDateTime.now(), projectId),
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

        @Test
        fun `returns only current user's chats`() {
            val request = GetChatsRequest(limit = 5)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatRepository.findAllByUserId(userId, any<Pageable>()) } returns PageImpl(allChats)

            val result = chatService.getChatsForCurrentUser(authId, request)

            assertEquals(5, result.chats.size)
            assertEquals(allChats[0].toChatResponse(), result.chats[0])
            verify(exactly = 1) { chatRepository.findAllByUserId(userId, any<Pageable>()) }
        }

        @Test
        fun `throws not found when current user cannot be resolved for chat list`() {
            val request = GetChatsRequest(limit = null)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()

            val ex = assertFailsWith<ResponseStatusException> {
                chatService.getChatsForCurrentUser(authId, request)
            }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
            verify(exactly = 0) { chatRepository.findAllByUserId(any(), any<Pageable>()) }
        }
    }

    @Nested
    inner class GetChat {
        private val chat = Chat(UUID.randomUUID(), "Some test chat", userId, OffsetDateTime.now(), projectId)
        private val chatMessages = listOf(
            ChatMessage(
                UUID.randomUUID(),
                ChatRole.USER,
                chat,
                mutableListOf(),
                "First message",
                OffsetDateTime.now(),
            ),
            ChatMessage(
                UUID.randomUUID(),
                ChatRole.USER,
                chat,
                mutableListOf(),
                "Second message",
                OffsetDateTime.now(),
            ),
            ChatMessage(
                UUID.randomUUID(),
                ChatRole.USER,
                chat,
                mutableListOf(),
                "Third message",
                OffsetDateTime.now(),
            ),
            ChatMessage(
                UUID.randomUUID(),
                ChatRole.USER,
                chat,
                mutableListOf(),
                "Fourth message",
                OffsetDateTime.now(),
            ),
            ChatMessage(
                UUID.randomUUID(),
                ChatRole.USER,
                chat,
                mutableListOf(),
                "Fifth message",
                OffsetDateTime.now(),
            ),
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

        @Test
        fun `returns current user's chat messages for owned chat`() {
            val request = GetChatMessagesRequest(limit = null)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatRepository.findByIdAndUserId(chat.id, userId) } returns Optional.of(chat)
            every {
                chatMessageRepository.findAllByChat(chat.id, any<Pageable>())
            } returns PageImpl(chatMessages)

            val result = chatService.getChatForCurrentUser(authId, chat.id, request)

            assertEquals(5, result.messages.size)
            assertEquals(chatMessages[0].toChatMessageResponse(), result.messages[0])
            verify(exactly = 1) { chatRepository.findByIdAndUserId(chat.id, userId) }
        }

        @Test
        fun `throws not found and does not load messages for foreign chat`() {
            val request = GetChatMessagesRequest(limit = null)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatRepository.findByIdAndUserId(chat.id, userId) } returns Optional.empty()

            val ex = assertFailsWith<ResponseStatusException> {
                chatService.getChatForCurrentUser(authId, chat.id, request)
            }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
            verify(exactly = 0) { chatMessageRepository.findAllByChat(any(), any<Pageable>()) }
        }
    }

    @Nested
    inner class CreateChat {
        @Test
        fun `creates chat with correct userId and returns its id`() {
            val request = CreateChatRequest(userId = userId, projectId = projectId)
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
            val request = CreateChatRequest(userId = userId, projectId = projectId)
            val chatSlot = slot<Chat>()
            every { chatRepository.save(capture(chatSlot)) } answers { chatSlot.captured }
            every { userApi.exists(any()) } returns false

            assertThrows<HttpClientErrorException> { chatService.createChat(request) }

            verify(exactly = 0) { chatRepository.save(any()) }
        }

        @Test
        fun `creates chat for current user resolved from auth id`() {
            val chatSlot = slot<Chat>()
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatRepository.save(capture(chatSlot)) } answers { chatSlot.captured }

            val result = chatService.createChatForCurrentUser(authId, projectId)

            assertEquals(userId, chatSlot.captured.userId)
            assertEquals(projectId, chatSlot.captured.projectId)
            assertEquals(chatSlot.captured.id, result.id)
            verify(exactly = 1) { chatRepository.save(any()) }
        }
    }

    @Nested
    inner class PromptAi {
        private val chatId = UUID.randomUUID()

        private fun mockOwnedChat(chat: Chat) {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatRepository.findByIdAndUserId(chat.id, userId) } returns Optional.of(chat)
        }

        @Test
        fun `rejects a chat that has no project instead of prompting unscoped`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Chat from before project scoping",
                createdAt = OffsetDateTime.now(),
                projectId = null,
            )
            mockOwnedChat(chat)

            val error = assertFailsWith<ResponseStatusException> {
                chatService
                    .promptForCurrentUser(authId, PromptRequest(chatId = chatId, msg = "Hello"))
                    .toList()
            }

            assertEquals(HttpStatus.CONFLICT, error.statusCode)
            // The AI service is never reached: an unscoped request would be answered from an
            // empty corpus, which reads as "the assistant knows nothing" rather than an error.
            coVerify(exactly = 0) { chatAiClient.streamPrompt(any()) }
            verify(exactly = 0) { chatMessageRepository.save(any()) }
        }

        @Test
        fun `emits tokens from ai stream`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing title",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            val aiPromptRequest = AiPromptRequest("Hello", listOf(), projectId.toString())
            val tokens = listOf(
                AiStreamMessage("token", "Hello"),
                AiStreamMessage("token", " world"),
                AiStreamMessage("done"),
            )
            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { citationRepository.saveAll(any<List<Citation>>()) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            coEvery { chatAiClient.streamPrompt(aiPromptRequest) } returns flowOf(*tokens.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            val result = chatService
                .promptForCurrentUser(
                    authId,
                    PromptRequest(chatId = chatId, msg = "Hello"),
                ).toList()

            assertEquals(tokens, result)
        }

        @Test
        fun `forwards tool_use events without accumulating them into the saved message`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing title",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            val aiPromptRequest = AiPromptRequest("Hello", listOf(), projectId.toString())
            val stream = listOf(
                AiStreamMessage(type = "tool_use", name = "retrieve", kind = "tool"),
                AiStreamMessage("token", "Hello"),
                AiStreamMessage("token", " world"),
                AiStreamMessage("done"),
            )
            val savedMessages = mutableListOf<ChatMessage>()
            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(capture(savedMessages)) } answers { firstArg() }
            every { citationRepository.saveAll(any<List<Citation>>()) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            every { chatAiClient.streamPrompt(aiPromptRequest) } returns flowOf(*stream.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            val emitted = chatService
                .promptForCurrentUser(
                    authId,
                    PromptRequest(chatId = chatId, msg = "Hello"),
                ).toList()

            // The tool_use event is forwarded downstream untouched
            assertEquals(stream, emitted)
            // But only the token content is persisted as the assistant message
            assertEquals(2, savedMessages.size)
            assertEquals(ChatRole.ASSISTANT, savedMessages[1].role)
            assertEquals("Hello world", savedMessages[1].content)
        }

        @Test
        fun `forwards reasoning events without accumulating them into the saved message`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing title",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            val aiPromptRequest = AiPromptRequest("Hello", listOf(), projectId.toString())
            val stream = listOf(
                AiStreamMessage(type = "reasoning", reasoning = "I am thinking..."),
                AiStreamMessage("token", "Hello"),
                AiStreamMessage(type = "reasoning", reasoning = "Let me continue..."),
                AiStreamMessage("token", " world"),
                AiStreamMessage("done"),
            )
            val savedMessages = mutableListOf<ChatMessage>()
            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(capture(savedMessages)) } answers { firstArg() }
            every { citationRepository.saveAll(any<List<Citation>>()) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            every { chatAiClient.streamPrompt(aiPromptRequest) } returns flowOf(*stream.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            val emitted = chatService
                .promptForCurrentUser(
                    authId,
                    PromptRequest(chatId = chatId, msg = "Hello"),
                ).toList()

            // Reasoning events are forwarded downstream untouched
            assertEquals(stream, emitted)
            // But only token content is persisted as the assistant message
            assertEquals(2, savedMessages.size)
            assertEquals(ChatRole.ASSISTANT, savedMessages[1].role)
            assertEquals("Hello world", savedMessages[1].content)
        }

        @Test
        fun `saves ai response as message on stream completion`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing title",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            val aiPromptRequest = AiPromptRequest("Hello", listOf(), projectId.toString())
            val tokens = listOf(
                AiStreamMessage("token", "Hello"),
                AiStreamMessage("token", " world"),
            )
            val savedMessages = mutableListOf<ChatMessage>()
            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(capture(savedMessages)) } answers { firstArg() }
            every { citationRepository.saveAll(any<List<Citation>>()) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            every { chatAiClient.streamPrompt(aiPromptRequest) } returns flowOf(*tokens.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            chatService
                .promptForCurrentUser(
                    authId,
                    PromptRequest(chatId = chatId, msg = "Hello"),
                ).toList() // collect to trigger completion

            // First save = user message, second save = AI response
            assertEquals(2, savedMessages.size)
            assertEquals(ChatRole.ASSISTANT, savedMessages[1].role)
            assertEquals("Hello world", savedMessages[1].content)
        }

        @Test
        fun `generates and saves title when chat title is blank`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            val aiPromptRequest = AiPromptRequest("Hello", listOf(), projectId.toString())
            mockOwnedChat(chat)
            every { chatRepository.save(any()) } answers { firstArg() }
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { citationRepository.saveAll(any<List<Citation>>()) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            coEvery { chatAiClient.getChatTitle(any()) } returns AiGenerateChatTitleResponse("Sprint planning")
            every { chatAiClient.streamPrompt(aiPromptRequest) } returns flowOf()
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            chatService
                .promptForCurrentUser(
                    authId,
                    PromptRequest(chatId = chatId, msg = "Hello"),
                ).toList()

            assertEquals("Sprint planning", chat.title)
            verify { chatRepository.save(chat) }
        }

        @Test
        fun `skips title generation when chat title is not blank`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { citationRepository.saveAll(any<List<Citation>>()) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            every { chatAiClient.streamPrompt(any()) } returns flowOf()
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            chatService
                .promptForCurrentUser(
                    authId,
                    PromptRequest(chatId = chatId, msg = "Hello"),
                ).toList()

            coVerify(exactly = 0) { chatAiClient.getChatTitle(any()) }
            verify(exactly = 0) { chatRepository.save(any()) }
        }

        @Test
        fun `throws when chat is not found`() = runTest {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatRepository.findByIdAndUserId(chatId, userId) } returns Optional.empty()

            val ex = assertFailsWith<ResponseStatusException> {
                chatService
                    .promptForCurrentUser(
                        authId,
                        PromptRequest(chatId = chatId, msg = "Hello"),
                    ).toList()
            }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        }

        @Test
        fun `throws not found before saving message when current user prompts foreign chat`() = runTest {
            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatRepository.findByIdAndUserId(chatId, userId) } returns Optional.empty()

            val ex = assertFailsWith<ResponseStatusException> {
                chatService
                    .promptForCurrentUser(
                        authId,
                        PromptRequest(chatId = chatId, msg = "Hello"),
                    ).toList()
            }

            assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
            verify(exactly = 0) { chatMessageRepository.save(any()) }
        }

        @Test
        fun `does not save ai response when stream errors`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            every { chatAiClient.streamPrompt(any()) } returns flow {
                emit(AiStreamMessage("token", "Hello"))
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException("AI backend unreachable")
            }

            // Collect and ignore the error — we're testing the side effect (no AI message saved)
            runCatching {
                chatService
                    .promptForCurrentUser(
                        authId,
                        PromptRequest(chatId = chatId, msg = "Hello"),
                    ).toList()
            }

            // Only the user message should have been saved, not the AI response
            verify(exactly = 1) { chatMessageRepository.save(any()) }
        }

        @Test
        fun `persists citations after ai response`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing title",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )

            val artifactId1 = UUID.randomUUID()
            val artifactId2 = UUID.randomUUID()

            val stream = listOf(
                AiStreamMessage(
                    type = "token",
                    content = "Hello",
                ),
                AiStreamMessage(
                    type = "citation",
                    artifactId = artifactId1.toString(),
                    startLine = 12,
                ),
                AiStreamMessage(
                    type = "citation",
                    artifactId = artifactId2.toString(),
                    startPage = 3,
                ),
                AiStreamMessage(type = "done"),
            )

            val savedMessages = mutableListOf<ChatMessage>()
            val citationSlot = slot<Iterable<Citation>>()

            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(capture(savedMessages)) } answers { firstArg() }
            every { citationRepository.saveAll(capture(citationSlot)) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            every { artifactLookupService.resolve(artifactId1) } returns
                ResolvedArtifact(filename = "architecture.md", sourceUrl = null)
            every { artifactLookupService.resolve(artifactId2) } returns
                ResolvedArtifact(filename = "backend.md", sourceUrl = "https://github.com/example/backend.md")

            coEvery { chatAiClient.streamPrompt(any()) } returns flowOf(*stream.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            val emitted = chatService.promptForCurrentUser(authId, PromptRequest(chatId, "Hello")).toList()

            val emittedCitations = emitted.filter { it.type == "citation" }
            assertEquals(2, emittedCitations.size)
            assertEquals("architecture.md", emittedCitations[0].filename)
            assertEquals("backend.md", emittedCitations[1].filename)
            assertEquals("https://github.com/example/backend.md", emittedCitations[1].sourceUrl)

            assertEquals(2, savedMessages.size)
            assertEquals(ChatRole.ASSISTANT, savedMessages[1].role)

            val savedCitations = citationSlot.captured.toList()

            assertEquals(2, savedCitations.size)

            assertEquals(artifactId1, savedCitations[0].artifactId)
            assertEquals("architecture.md", savedCitations[0].filename)
            assertEquals(12, savedCitations[0].startLine)

            assertEquals(artifactId2, savedCitations[1].artifactId)
            assertEquals("backend.md", savedCitations[1].filename)
            assertEquals("https://github.com/example/backend.md", savedCitations[1].sourceUrl)
            assertEquals(3, savedCitations[1].startPage)
            assertEquals(savedMessages[1], savedCitations[0].message)
            assertEquals(savedMessages[1], savedCitations[1].message)
        }

        @Test
        fun `skips citations whose artifact cannot be resolved`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing title",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )
            val unknownArtifactId = UUID.randomUUID()

            val stream = listOf(
                AiStreamMessage(
                    type = "citation",
                    artifactId = unknownArtifactId.toString(),
                ),
                AiStreamMessage(type = "done"),
            )

            val citationSlot = slot<Iterable<Citation>>()

            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { citationRepository.saveAll(capture(citationSlot)) } answers { firstArg() }
            every { connectorConfigurationService.findAllConnectors() } returns emptyList()
            every { artifactLookupService.resolve(unknownArtifactId) } returns null

            coEvery { chatAiClient.streamPrompt(any()) } returns flowOf(*stream.toTypedArray())
            every { applicationConfig.ai.baseUrl } returns "http://localhost:8080"

            val emitted = chatService.promptForCurrentUser(authId, PromptRequest(chatId, "Hello")).toList()

            assertEquals(0, emitted.count { it.type == "citation" })
            assertEquals(0, citationSlot.captured.toList().size)
        }

        @Test
        fun `forwards chat filters to ai`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )

            mockOwnedChat(chat)
            every { chatMessageRepository.findAllByChat(any(), any()) } returns PageImpl(emptyList())
            every { chatMessageRepository.save(any()) } answers { firstArg() }
            every { citationRepository.saveAll(any<List<Citation>>()) } answers { firstArg() }

            every {
                chatAiClient.streamPrompt(
                    match {
                        it.filters?.sourceSystems == listOf(SourceSystem.GITHUB)
                    },
                )
            } returns flowOf(AiStreamMessage("done"))

            every {
                connectorConfigurationService.findAllConnectors()
            } returns listOf(
                ConnectorDto(
                    id = "github",
                    // The real display name: this is what ConnectorConfigurationService puts in
                    // `name`, and matching the SourceSystem enum against it is what used to fail.
                    name = "Github Repository Connector",
                    enabled = true,
                    firstConfiguredAt = null,
                    lastConfiguredAt = null,
                ),
            )

            chatService
                .promptForCurrentUser(
                    authId,
                    PromptRequest(
                        chatId = chatId,
                        msg = "Hello",
                        filters = ChatFilters(
                            sourceSystems = listOf(SourceSystem.GITHUB),
                            from = null,
                            to = null,
                        ),
                    ),
                ).toList()
        }

        @Test
        fun `throws when requested connector is disabled`() = runTest {
            val chat = Chat(
                id = chatId,
                userId = userId,
                title = "Existing",
                createdAt = OffsetDateTime.now(),
                projectId = projectId,
            )

            mockOwnedChat(chat)

            every {
                chatMessageRepository.findAllByChat(any(), any())
            } returns PageImpl(emptyList())

            every {
                connectorConfigurationService.findAllConnectors()
            } returns listOf(
                ConnectorDto(
                    id = "github",
                    // The real display name: this is what ConnectorConfigurationService puts in
                    // `name`, and matching the SourceSystem enum against it is what used to fail.
                    name = "Github Repository Connector",
                    enabled = false,
                    firstConfiguredAt = null,
                    lastConfiguredAt = null,
                ),
            )

            assertFailsWith<ConnectorDisabledException> {
                chatService
                    .promptForCurrentUser(
                        authId,
                        PromptRequest(
                            chatId = chatId,
                            msg = "Hello",
                            filters = ChatFilters(
                                sourceSystems = listOf(SourceSystem.GITHUB),
                                from = null,
                                to = null,
                            ),
                        ),
                    ).toList()
            }
        }
    }
}
