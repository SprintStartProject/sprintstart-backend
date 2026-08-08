package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.ChatAiClient
import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.Citation
import com.sprintstart.sprintstartbackend.chat.models.requests.AiGenerateChatTitleRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.AiPromptRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.PromptRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.toAiChatFilters
import com.sprintstart.sprintstartbackend.chat.models.requests.toAiContextEntry
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.CreateChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatMessagesResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatsResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.toChatResponse
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.chat.repository.CitationRepository
import com.sprintstart.sprintstartbackend.connectors.overview.external.api.ConnectorOverviewApi
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.ConnectorDisabledException
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.ConnectorNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Validated
internal class ChatService(
    private val chatRepository: ChatRepository,
    private val messageRepository: ChatMessageRepository,
    private val citationRepository: CitationRepository,
    private val connectorOverviewApi: ConnectorOverviewApi,
    private val chatAiClient: ChatAiClient,
    private val userApi: UserApi,
    private val artifactLookupService: ArtifactLookupService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
        val userId = resolveCurrentUserId(userApi, authId)
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
        val userId = resolveCurrentUserId(userApi, authId)
        val chat = findOwnedChat(chatRepository, chatId, userId)
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
        val userId = resolveCurrentUserId(userApi, authId)
        val chat = Chat(
            userId = userId,
            projectId = projectId,
            createdAt = OffsetDateTime.now(),
        )

        chatRepository.save(chat)
        return CreateChatResponse(chat.id)
    }

    /**
     * Prompts the AI only for a chat owned by the authenticated user.
     *
     * The chat id remains in the request because it identifies the conversation, but ownership is
     * checked before any context is loaded, message is stored, or AI stream is opened.
     *
     * @param authId External authentication identifier from the authenticated JWT.
     * @param request The prompt payload.
     * @return A stream of AI events for the owned chat.
     * @throws ResponseStatusException `404` when the authenticated user or owned chat does not exist.
     */
    @Tracked("Prompting the AI system for the current user's chat")
    suspend fun promptForCurrentUser(authId: String, @Valid request: PromptRequest): Flow<AiStreamMessage> {
        val userId = resolveCurrentUserId(userApi, authId)
        val chat = findOwnedChat(chatRepository, request.chatId, userId)
        return promptExistingChat(chat, request)
    }

    private suspend fun promptExistingChat(
        chat: Chat,
        request: PromptRequest,
    ): Flow<AiStreamMessage> {
        // Chats created before project scoping existed have no project, and there is no honest way
        // to infer one. The AI service requires a scope, so those chats stay readable but cannot be
        // prompted — surfaced as a 409 rather than a silently empty answer.
        val projectId = chat.projectId ?: throw ResponseStatusException(
            HttpStatus.CONFLICT,
            "Chat ${chat.id} predates project scoping and cannot be prompted. Start a new chat.",
        )

        val msg = ChatMessage(
            role = ChatRole.USER,
            chat = chat,
            createdAt = OffsetDateTime.now(),
            content = request.msg,
        )

        // If the title is empty, the chat is new, and a title needs to be generated
        if (chat.title.isBlank()) {
            val generatedTitle = chatAiClient.getChatTitle(AiGenerateChatTitleRequest(request.msg))
            chat.title = generatedTitle.title
            chatRepository.save(chat)
        }

        // Read messages before saving new message so it isn't sent double
        val context = messageRepository
            .findAllByChat(
                chat.id,
                Pageable.unpaged(Sort.by(Sort.Direction.ASC, "created_at")),
            ).map { it.toAiContextEntry() }
            .toList()

        validateSourceSystems(request.filters?.sourceSystems)
        validateTimestamps(request.filters?.from, request.filters?.to)

        val filters = request.filters?.toAiChatFilters()

        messageRepository.save(msg)

        data class PendingCitation(
            val id: UUID,
            val artifactId: UUID,
            val filename: String,
            val sourceUrl: String?,
            val startLine: Int?,
            val startPage: Int?,
        )

        val sb = StringBuilder()
        val citations = mutableListOf<PendingCitation>()

        // Define stream handler
        // - On each token we collect the token and emit it to the controller
        // - On each citation we resolve filename/sourceUrl (the AI service only sends
        //   the artifact id + position) so the client sees a complete citation live,
        //   not only once the chat is reloaded from persisted history
        // - On completion, we store the entire response as msg in db
        return chatAiClient
            .streamPrompt(AiPromptRequest(request.msg, context, projectId.toString(), filters))
            .map { event ->
                when (event.type) {
                    "token" -> {
                        event.content?.let(sb::append)
                        event
                    }

                    "citation" -> {
                        val artifactId = event.artifactId?.let(::parseUuidOrNull)
                        val resolved = artifactId?.let(artifactLookupService::resolve)
                        if (artifactId == null || resolved == null) {
                            logger.warn("Could not resolve artifact {} for citation", event.artifactId)
                            null
                        } else {
                            citations += PendingCitation(
                                id = UUID.randomUUID(),
                                artifactId = artifactId,
                                filename = resolved.filename,
                                sourceUrl = resolved.sourceUrl,
                                startLine = event.startLine,
                                startPage = event.startPage,
                            )
                            event.copy(filename = resolved.filename, sourceUrl = resolved.sourceUrl)
                        }
                    }

                    else -> {
                        event
                    }
                }
            }.filterNotNull()
            .onCompletion { cause ->
                if (cause == null) {
                    val msg = ChatMessage(
                        role = ChatRole.ASSISTANT,
                        chat = chat,
                        content = sb.toString(),
                        createdAt = OffsetDateTime.now(),
                    )

                    messageRepository.save(msg)

                    val citationEntities = citations.map { pending ->
                        Citation(
                            id = pending.id,
                            artifactId = pending.artifactId,
                            filename = pending.filename,
                            sourceUrl = pending.sourceUrl,
                            startLine = pending.startLine,
                            startPage = pending.startPage,
                            message = msg,
                        )
                    }

                    citationRepository.saveAll(citationEntities)
                } else {
                    println("Stream either got killed or experienced an error: ${cause.message}")
                }
            }
    }

    /**
     * Checks if the source systems are valid.
     *
     * Checks if all the provided source systems exist and are enabled.
     *
     * @param sourceSystems The systems used by the AI to generate responses.
     */
    private fun validateSourceSystems(sourceSystems: List<SourceSystem>?) {
        val connectorsByName = connectorOverviewApi.findAllConnectors().associateBy { it.name }

        sourceSystems?.forEach { sourceSystem ->
            val connector = connectorsByName[sourceSystem.name]
                ?: throw ConnectorNotFoundException("Connector '${sourceSystem.name}' not found")

            if (!connector.enabled) {
                throw ConnectorDisabledException("Connector '${sourceSystem.name}' is disabled")
            }
        }
    }

    /**
     * Checks if the time stamps are valid.
     *
     * Checks if the 'from' timestamp is before the 'to' timestamp.
     *
     * @param from The start of the time period transferred to the AI.
     * @param to The end of the time period transferred to the AI.
     */
    private fun validateTimestamps(from: Instant?, to: Instant?) {
        if (from != null && to != null && to.isBefore(from)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be before end time.")
        }
    }
}

/**
 * Parses [value] as a [UUID], or null if it isn't one.
 *
 * A malformed citation artifact id from the AI service should degrade that one citation,
 * not kill the whole chat stream the way [UUID.fromString]'s thrown exception would.
 */
private fun parseUuidOrNull(value: String): UUID? =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
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

private fun resolveCurrentUserId(userApi: UserApi, authId: String): UUID {
    return userApi.getUserIdByAuthId(authId).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found")
    }
}

private fun findOwnedChat(chatRepository: ChatRepository, chatId: UUID, userId: UUID): Chat {
    return chatRepository.findByIdAndUserId(chatId, userId).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Chat with id $chatId not found")
    }
}
