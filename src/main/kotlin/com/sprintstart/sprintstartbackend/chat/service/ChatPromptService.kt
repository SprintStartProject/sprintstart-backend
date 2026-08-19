package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.ChatAiClient
import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.Citation
import com.sprintstart.sprintstartbackend.chat.models.requests.AiGenerateChatTitleRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.AiPromptRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.PromptRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.toAiChatFilters
import com.sprintstart.sprintstartbackend.chat.models.requests.toAiContextEntry
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
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
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Validated
internal class ChatPromptService(
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
    suspend fun promptForCurrentUser(
        authId: String,
        @Valid request: PromptRequest,
    ): Flow<AiStreamMessage> {
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
        if (sourceSystems.isNullOrEmpty()) return

        // Keyed by connector *id* ("github", "jira"), not by `name` — that field holds the display
        // name ("GitHub Repository Connector"), so matching it against the SourceSystem enum
        // constant could never succeed and every source filter failed with "connector not found".
        val connectorsById = connectorOverviewApi.findAllConnectors().associateBy { it.id.lowercase() }

        sourceSystems.forEach { sourceSystem ->
            // Uploads are not a connector: they have no configuration to enable or disable and are
            // always available, so there is nothing to look up.
            if (sourceSystem == SourceSystem.UPLOAD) return@forEach

            val connector = connectorsById[sourceSystem.name.lowercase()]
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
