package com.sprintstart.sprintstartbackend.chat.controller

import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.PromptRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.CreateChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatMessagesResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatsResponse
import com.sprintstart.sprintstartbackend.chat.service.ChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Chat", description = "Endpoints for interacting with ai chats")
@RestController
@RequestMapping("/api/v1/chats")
internal class ChatController(
    private val chatService: ChatService,
) {
    @Operation(
        summary = "Retrieves chats with their metadata (No messages!)",
        description =
            "Retrieves the n chats that were last interacted with, including only their metadata, not the messages!" +
                " Quick side note: not passing the limit fetches all.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Chats retrieved successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    fun getChats(@Valid @RequestBody request: GetChatsRequest): GetChatsResponse {
        return chatService.getChats(request)
    }

    @Operation(
        summary = "Retrieves a chat's messages",
        description = "Retrieves the n last messages of a specific chat. Not specifying the limit fetches all.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Messages retrieved successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{id}")
    fun getChatMessages(
        @PathVariable id: UUID,
        @Valid @RequestBody request: GetChatMessagesRequest,
    ): GetChatMessagesResponse {
        return chatService.getChat(id, request)
    }

    @Operation(
        summary = "Initializes a new chat",
        description = "Creates a new chat and persists it in the db",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Chat successfully created"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createChat(@Valid @RequestBody request: CreateChatRequest): CreateChatResponse {
        return chatService.createChat(request)
    }

    @Operation(
        summary = "Prompts the ai",
        description = "Adds a new prompt to a chat, await ai response",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Stream started successfully, tokens will now come one by one",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(
                            examples = [
                                "data: {\"type\": \"token\", \"content\": \"The main\"}",
                                "data: {\"type\": \"citation\", \"chunk_id\": \"chunk-1\"," +
                                    "\"filename\": \"retro.md\", \"section_path\": \"Retro > Blockers\"}",
                                "data: {\"type\": \"done\"}",
                                "data: {\": \"error\", \"message\": \"LLM backend unreachable\"}",
                            ],
                        ),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "500", description = "Internal server error"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/prompt", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun prompt(@Valid @RequestBody request: PromptRequest): Flow<String> {
        return chatService.prompt(request)
    }
}
