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
import com.sprintstart.sprintstartbackend.user.external.UserApi
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import org.springframework.web.client.HttpClientErrorException
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Validated
internal class ChatService(
    private val applicationConfig: ApplicationConfig,
    private val chatRepository: ChatRepository,
    private val messageRepository: ChatMessageRepository,
    private val webRequestClient: AiWebClientImpl,
    private val userApi: UserApi,
) {
    /**
     * Retrieves the n latest chats without their messages. N is determined by `request.limit`.
     *
     * This function retrieves the n latest chats, including only their metadata, not the messages.
     * As AI chats can get quite long, loading all chat's messages would take up way too many resources,
     * especially when facing the fact that the user will most likely only open 1 or 2.
     * For this reason the messages are not included, but can be lazy-loaded using the chat id.
     *
     * @param request The request including the necessary data to calculate the response.
     * @return The [GetChatsResponse] including the chats and their metadata
     * @see GetChatsRequest
     * @see GetChatsResponse
     */
    @Transactional(readOnly = true)
    fun getChats(@Valid request: GetChatsRequest): GetChatsResponse {
        val pageable = if (request.limit == null) {
            Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt"))
        } else {
            PageRequest.of(0, request.limit, Sort.Direction.DESC, "createdAt")
        }

        val chats = chatRepository.findAll(pageable)
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
    fun getChat(chatId: UUID, @Valid request: GetChatMessagesRequest): GetChatMessagesResponse {
        val pageable = if (request.limit == null) {
            Pageable.unpaged(Sort.by(Sort.Direction.DESC, "created_at"))
        } else {
            PageRequest.of(0, request.limit, Sort.Direction.DESC, "created_at")
        }

        val msgs = messageRepository.findAllByChat(chatId, pageable).map { it.toChatMessageResponse() }.toList()
        return GetChatMessagesResponse(
            messages = msgs,
        )
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
    fun createChat(@Valid request: CreateChatRequest): CreateChatResponse {
        if (!userApi.exists(request.userId)) {
            throw HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Attempted to create chat with non-existing user",
            )
        }

        val chat = Chat(
            userId = request.userId,
            createdAt = OffsetDateTime.now(),
        )

        chatRepository.save(chat)
        return CreateChatResponse(chat.id)
    }

    /**
     * Prompts the AI.
     *
     * This function takes a prompt, collects the relevant context and prompts the AI repo for a response.
     * The AI repo will answer with an SSE stream, that continuously sends new generated tokens to us.
     * When receiving these tokens, all we do is keep track of them and forward them to the caller.
     * When the AI repo is done with its answer, we store the collected response as a message in the db.
     * Also, if it's a new (empty) chat, the title will be generated by the initial prompt.
     *
     * @param request The request that contains the chat metadata as well as the actual prompt.
     * @return [Flow<String>] A flow of strings (tokens in this case).
     * @see PromptRequest
     * @see Flow
     */
    fun prompt(@Valid request: PromptRequest): Flow<String> {
        val chat = chatRepository.findById(request.chatId).orElseThrow {
            HttpClientErrorException(
                HttpStatus.BAD_REQUEST,
                "Attempted to prompt a non-existing chat",
            )
        }
        val msg = ChatMessage(
            role = ChatRole.USER,
            chat = chat,
            createdAt = OffsetDateTime.now(),
            content = request.msg,
        )

        // If the title is empty, the chat is new, and a title needs to be generated
        if (chat.title.isBlank()) {
            val uri = URI.create("${applicationConfig.ai.baseUrl}/generate-title")
            val generatedTitle = webRequestClient.getChatTitle(uri)
            chat.title = generatedTitle.title
            chatRepository.save(chat)
        }

        // Read messages before saving new message so it isn't sent double
        val context = messageRepository
            .findAllByChat(
                chat.id,
                Pageable.unpaged(Sort.by(Sort.Direction.ASC, "createdAt")),
            ).map { it.toAiContextEntry() }
            .toList()

        messageRepository.save(msg)

        // Make call to AI repo
        val promptingPayload = AiPromptRequest(request.msg, context)
        val stream = webRequestClient.streamPrompt(
            URI.create("${applicationConfig.ai.baseUrl}/prompt"),
            promptingPayload,
        )
        val sb = StringBuilder()

        // Define stream handler
        // - On each token we collect the token and emit it to the controller
        // - On completion, we store the entire response as msg in db
        return stream
            .onEach { rawJson ->
                try {
                    val message = webRequestClient.jsonParser.decodeFromString<AiStreamMessage>(rawJson)
                    message.content?.let { token ->
                        sb.append(token) // Collect tokens
                    }
                } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                    // We can technically ignore errors here too, as they're handled in the streamPost() method already
                    System.err.println("Error: " + e.message)
                }
            }.onCompletion { cause ->
                if (cause == null) {
                    val finalPromptResponse = sb.toString()
                    val msg = ChatMessage(
                        role = ChatRole.ASSISTANT,
                        chat = chat,
                        content = finalPromptResponse,
                        createdAt = OffsetDateTime.now(),
                    )
                    messageRepository.save(msg)
                } else {
                    println("Stream either got killed or experienced an error: ${cause.message}")
                }
            }
    }
}
