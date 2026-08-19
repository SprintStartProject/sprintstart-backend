package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.CreateChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatMessagesResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatsResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatResponse
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Validated
internal class ChatService(
    private val chatRepository: ChatRepository,
    private val messageRepository: ChatMessageRepository,
    private val userApi: UserApi,
    private val chatAuthService: ChatAuthService,
) {
    /**
     * Retrieves the n latest chats without their messages. N is determined by `request.limit`.
     *
     * This function retrieves the n latest chats, including only their metadata, not the messages.
     * As AI chats can get quite long, loading all chat's messages would take up way too many resources,
     * especially when facing the fact that the user will most likely only open 1 or 2.
     * For this reason the messages are not included but can be lazy-loaded using the chat id.
     *
     * @param request The request including the necessary data to calculate the response.
     * @return The [GetChatsResponse] including the chats and their metadata
     * @see GetChatsRequest
     * @see GetChatsResponse
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving last n chats")
    fun getChats(@Valid request: GetChatsRequest): GetChatsResponse {
        val pageable = chatPageableFor(request.limit)

        val chats = chatRepository.findAll(pageable)
        val chatResponses: List<ChatResponse> = chats.stream().map { it.toChatResponse() }.toList()
        return GetChatsResponse(chatResponses)
    }

    /**
     * Retrieves the authenticated user's latest chats without accepting a client-supplied user id.
     *
     * The JWT subject is resolved through the user module boundary, then used as an ownership
     * predicate for the chat query so normal users cannot enumerate another user's chats.
     *
     * @param authId External authentication identifier from the authenticated JWT.
     * @param request Query parameters controlling pagination.
     * @return The authenticated user's chat metadata.
     * @throws ResponseStatusException `404` when the authenticated user has no local projection.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving current user's last n chats")
    fun getChatsForCurrentUser(authId: String, @Valid request: GetChatsRequest): GetChatsResponse {
        val userId = chatAuthService.resolveCurrentUserId(userApi, authId)
        val pageable = chatPageableFor(request.limit)
        // Scoped to the requested project so the sidebar follows the project switcher. Chats
        // predating project scoping have no project and therefore appear in no list; they remain
        // readable through `GET /chats/me/{id}`.
        val chats = request.projectId
            ?.let { chatRepository.findAllByUserIdAndProjectId(userId, it, pageable) }
            ?: chatRepository.findAllByUserId(userId, pageable)
        val chatResponses: List<ChatResponse> = chats.stream().map { it.toChatResponse() }.toList()
        return GetChatsResponse(chatResponses)
    }

    /**
     * Retrieves a specific chat, including the respective messages.
     *
     * This function allows loading a specific chat (by id) and loading the n latest messages of it.
     * N is determined by `request.limit`. Specify the limit as -1, and all the messages are loaded.
     *
     * @param chatId The uuid of the chat to load.
     * @param request The request containing additional data, like the limit (n) of messages to load.
     * @return The [GetChatMessagesResponse] including the last n chat messages.
     * @see GetChatMessagesRequest
     * @see GetChatMessagesRequest
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving chat messages")
    fun getChat(chatId: UUID, @Valid request: GetChatMessagesRequest): GetChatMessagesResponse {
        return getChatMessages(messageRepository, chatId, request)
    }

    /**
     * Retrieves a chat's messages only when the chat belongs to the authenticated user.
     *
     * Foreign chats are treated the same as missing chats and return `404`, avoiding both data
     * exposure and resource-existence disclosure to normal users.
     *
     * @param authId External authentication identifier from the authenticated JWT.
     * @param chatId The chat id requested by the caller.
     * @param request Query parameters controlling message pagination.
     * @return The current user's chat messages.
     * @throws ResponseStatusException `404` when the authenticated user or owned chat does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving current user's chat messages")
    fun getChatForCurrentUser(
        authId: String,
        chatId: UUID,
        @Valid request: GetChatMessagesRequest,
    ): GetChatMessagesResponse {
        val userId = chatAuthService.resolveCurrentUserId(userApi, authId)
        val chat = chatAuthService.findOwnedChat(chatId, userId)
        return getChatMessages(messageRepository, chat.id, request)
    }

    /**
     * Initializes a new chat.
     *
     * This function creates a new chat based on the given metadata. It does nothing more than create the chat, save it,
     * then return its id.
     *
     * @param request Contains the new chat's metadata.
     * @return The [CreateChatResponse] containing all relevant information like the chat id.
     * @see CreateChatRequest
     * @see CreateChatResponse
     */
    @Tracked("Creating a new chat")
    fun createChat(@Valid request: CreateChatRequest): CreateChatResponse {
        if (!userApi.exists(request.userId)) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Attempted to create chat with non-existing user",
            )
        }

        val chat = Chat(
            userId = request.userId,
            projectId = request.projectId,
            createdAt = OffsetDateTime.now(),
        )

        chatRepository.save(chat)
        return CreateChatResponse(chat.id)
    }

    /**
     * Creates a chat owned by the authenticated user.
     *
     * The caller cannot choose the owner; ownership is resolved from the JWT subject so a user
     * cannot create chats under another user's id.
     *
     * @param authId External authentication identifier from the authenticated JWT.
     * @param projectId The project the chat is scoped to; the caller's access to it is verified by
     * the controller before this is reached.
     * @return The newly created chat id.
     * @throws ResponseStatusException `404` when the authenticated user has no local projection.
     */
    @Tracked("Creating a new chat for the current user")
    fun createChatForCurrentUser(authId: String, projectId: UUID): CreateChatResponse {
        val userId = chatAuthService.resolveCurrentUserId(userApi, authId)
        val chat = Chat(
            userId = userId,
            projectId = projectId,
            createdAt = OffsetDateTime.now(),
        )

        chatRepository.save(chat)
        return CreateChatResponse(chat.id)
    }

    /**
     * Deletes an existing chat.
     *
     * This function deletes a specific chat (by id) and all its contained messages, meaning all messages linked to
     * the specified chat.
     *
     * @param chatId The ID of the chat to be deleted.
     * @throws ResponseStatusException '404' when the specified chat does not exist.
     */
    @Transactional
    @Tracked("Deleting existing chat")
    fun deleteChat(chatId: UUID) {
        val chat = chatRepository.findById(chatId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Chat with id $chatId not found")
        }
        messageRepository.deleteAllByChatId(chatId)
        chatRepository.delete(chat)
    }

    /**
     * Deletes an existing chat created by the current user.
     *
     * This function deletes both the chat and all its contained messages. Only works for chats owned by the current
     * user, e.g., chats that were created by the current user.
     *
     * @param authId ID used for verifying the current user.
     * @param chatId The ID of the chat to be deleted.
     * @throws ResponseStatusException '404' when the specified chat does not exist or does not belong to the
     * authenticated user.
     */
    @Transactional
    @Tracked("Deleting existing chat created by the current user")
    fun deleteChatForCurrentUser(authId: String, chatId: UUID) {
        val userId = chatAuthService.resolveCurrentUserId(userApi, authId)
        val chat = chatAuthService.findOwnedChat(chatId, userId)
        messageRepository.deleteAllByChatId(chatId)
        chatRepository.delete(chat)
    }

    /**
     * Deletes a message from any chat.
     *
     * @param messageId The ID of the message to be deleted.
     * @throws ResponseStatusException '404' when the specified message does not exist.
     */
    @Transactional
    @Tracked("Deleting message from chat")
    fun deleteMessage(messageId: UUID) {
        val message = messageRepository.findById(messageId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Message with id $messageId not found")
        }
        messageRepository.delete(message)
    }

    /**
     * Deletes a message from a chat owned by the current user.
     *
     * Only works if the message belongs to a chat that is owned by the currently authenticated user.
     *
     * @param authId ID used for verifying the current user.
     * @param messageId The ID of the message to be deleted.
     * @throws ResponseStatusException '404' when the message does not exist or does not belong to a chat owned by the
     * current user.
     */
    @Transactional
    @Tracked("Deleting message from chat owned by the current user")
    fun deleteMessageForCurrentUser(authId: String, messageId: UUID) {
        val userId = chatAuthService.resolveCurrentUserId(userApi, authId)
        val message = messageRepository.findById(messageId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Message with id $messageId not found")
        }
        val chat = message.chat
        if (chat.userId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Message with id $messageId not found for current user")
        }
        messageRepository.delete(message)
    }
}

private fun getChatMessages(
    messageRepository: ChatMessageRepository,
    chatId: UUID,
    request: GetChatMessagesRequest,
): GetChatMessagesResponse {
    val pageable = if (request.limit == null) {
        Pageable.unpaged(Sort.by(Sort.Direction.ASC, "created_at"))
    } else {
        PageRequest.of(0, request.limit, Sort.Direction.ASC, "created_at")
    }

    val msgs = messageRepository.findAllByChat(chatId, pageable).map { it.toChatMessageResponse() }.toList()
    return GetChatMessagesResponse(
        messages = msgs,
    )
}

private fun chatPageableFor(limit: Int?): Pageable {
    return if (limit == null) {
        Pageable.unpaged(Sort.by(Sort.Direction.ASC, "createdAt"))
    } else {
        PageRequest.of(0, limit, Sort.Direction.ASC, "createdAt")
    }
}
