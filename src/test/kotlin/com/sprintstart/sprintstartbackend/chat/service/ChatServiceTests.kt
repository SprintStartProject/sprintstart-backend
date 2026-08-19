package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatResponse
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val chatRepository: ChatRepository = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk()
    private val userApi: UserApi = mockk()
    private val chatAuthService: ChatAuthService = mockk()
    private val chatService = ChatService(
        chatRepository,
        chatMessageRepository,
        userApi,
        chatAuthService,
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
            every {
                chatRepository.findAllByUserId(userId, any<Pageable>())
            } returns PageImpl(allChats)
            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId

            val result = chatService.getChatsForCurrentUser(authId, request)

            assertEquals(5, result.chats.size)
            assertEquals(allChats[0].toChatResponse(), result.chats[0])
            verify(exactly = 1) { chatRepository.findAllByUserId(userId, any<Pageable>()) }
        }

        @Test
        fun `throws not found when current user cannot be resolved for chat list`() {
            val request = GetChatsRequest(limit = null)
            every { userApi.getUserIdByAuthId(authId) } returns Optional.empty()
            every {
                chatAuthService.resolveCurrentUserId(userApi, authId)
            } throws ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Authenticated user not found",
            )

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
            every {
                chatMessageRepository.findAllByChat(chat.id, any<Pageable>())
            } returns PageImpl(chatMessages)
            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId
            every { chatAuthService.findOwnedChat(chat.id, userId) } returns chat

            val result = chatService.getChatForCurrentUser(authId, chat.id, request)

            assertEquals(5, result.messages.size)
            assertEquals(chatMessages[0].toChatMessageResponse(), result.messages[0])
            verify(exactly = 1) { chatAuthService.resolveCurrentUserId(userApi, authId) }
            verify(exactly = 1) { chatAuthService.findOwnedChat(chat.id, userId) }
        }

        @Test
        fun `throws not found and does not load messages for foreign chat`() {
            val request = GetChatMessagesRequest(limit = null)
            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId
            every {
                chatAuthService.findOwnedChat(chat.id, userId)
            } throws ResponseStatusException(HttpStatus.NOT_FOUND)

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
            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId

            val result = chatService.createChatForCurrentUser(authId, projectId)

            assertEquals(userId, chatSlot.captured.userId)
            assertEquals(projectId, chatSlot.captured.projectId)
            assertEquals(chatSlot.captured.id, result.id)
            verify(exactly = 1) { chatRepository.save(any()) }
        }
    }

    @Nested
    inner class DeleteChat {
        @Test
        fun `deletes chat and all its messages`() {
            val chatId = UUID.randomUUID()
            val chat = mockk<Chat>()

            every { chatRepository.findById(chatId) } returns Optional.of(chat)
            every { chatMessageRepository.deleteAllByChatId(chatId) } returns Unit
            every { chatRepository.delete(chat) } returns Unit

            chatService.deleteChat(chatId)

            verify(exactly = 1) { chatMessageRepository.deleteAllByChatId(chatId) }
            verify(exactly = 1) { chatRepository.delete(chat) }
        }

        @Test
        fun `throws not found when chat does not exist`() {
            val chatId = UUID.randomUUID()

            every { chatRepository.findById(chatId) } returns Optional.empty()

            assertThrows<ResponseStatusException> {
                chatService.deleteChat(chatId)
            }

            verify(exactly = 0) { chatRepository.delete(any()) }
            verify(exactly = 0) { chatMessageRepository.deleteAllByChatId(any()) }
        }

        @Test
        fun `deletes chat and all its messages for current user`() {
            val chatId = UUID.randomUUID()
            val chat = mockk<Chat>()

            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId
            every { chatAuthService.findOwnedChat(chatId, userId) } returns chat
            every { chatMessageRepository.deleteAllByChatId(chatId) } returns Unit
            every { chatRepository.delete(chat) } returns Unit

            chatService.deleteChatForCurrentUser(authId, chatId)

            verify(exactly = 1) { chatAuthService.resolveCurrentUserId(userApi, authId) }
            verify(exactly = 1) { chatAuthService.findOwnedChat(chatId, userId) }
            verify(exactly = 1) { chatMessageRepository.deleteAllByChatId(chatId) }
            verify(exactly = 1) { chatRepository.delete(chat) }
        }

        @Test
        fun `throws not found when current user does not own chat`() {
            val chatId = UUID.randomUUID()

            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId
            every { chatAuthService.findOwnedChat(chatId, userId) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

            assertThrows<ResponseStatusException> { chatService.deleteChatForCurrentUser(authId, chatId) }

            verify(exactly = 0) { chatRepository.delete(any()) }

            verify(exactly = 0) { chatMessageRepository.deleteAllByChatId(any()) }
        }
    }

    @Nested
    inner class DeleteMessage {
        @Test
        fun `deletes message owned by current user`() {
            val messageId = UUID.randomUUID()
            val message = mockk<ChatMessage>()
            val chat = mockk<Chat>()

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatMessageRepository.findById(messageId) } returns Optional.of(message)
            every { message.chat } returns chat
            every { chat.userId } returns userId
            every { chatMessageRepository.delete(message) } returns Unit
            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId

            chatService.deleteMessageForCurrentUser(authId, messageId)

            verify(exactly = 1) { chatMessageRepository.findById(messageId) }
            verify(exactly = 1) { chatMessageRepository.delete(message) }
        }

        @Test
        fun `throws not found when message does not exist`() {
            val messageId = UUID.randomUUID()

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatMessageRepository.findById(messageId) } returns Optional.empty()
            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId

            assertThrows<ResponseStatusException> {
                chatService.deleteMessageForCurrentUser(authId, messageId)
            }.also {
                assertEquals(HttpStatus.NOT_FOUND, it.statusCode)
            }

            verify(exactly = 0) { chatMessageRepository.delete(any()) }
        }

        @Test
        fun `throws not found when message belongs to another user`() {
            val messageId = UUID.randomUUID()
            val message = mockk<ChatMessage>()
            val chat = mockk<Chat>()
            val otherUserId = UUID.randomUUID()

            every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
            every { chatMessageRepository.findById(messageId) } returns Optional.of(message)
            every { message.chat } returns chat
            every { chat.userId } returns otherUserId
            every { chatAuthService.resolveCurrentUserId(userApi, authId) } returns userId

            assertThrows<ResponseStatusException> {
                chatService.deleteMessageForCurrentUser(authId, messageId)
            }.also {
                assertEquals(HttpStatus.NOT_FOUND, it.statusCode)
            }

            verify(exactly = 0) { chatMessageRepository.delete(any()) }
        }
    }
}
