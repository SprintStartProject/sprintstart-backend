package com.sprintstart.sprintstartbackend.chat.controller

import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateMyChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.PromptRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import com.sprintstart.sprintstartbackend.chat.models.responses.CreateChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatMessagesResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatsResponse
import com.sprintstart.sprintstartbackend.chat.service.ChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Chat", description = "Endpoints for interacting with ai chats")
@RestController
@RequestMapping("/api/v1/chats")
@Validated
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
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getChats(@RequestParam @Min(1) limit: Int?): GetChatsResponse {
        val request = GetChatsRequest(limit = limit)
        return chatService.getChats(request)
    }

    /**
     * Retrieves the authenticated user's chat metadata.
     *
     * The user is resolved from the JWT subject, so clients cannot enumerate another user's chats
     * by supplying a foreign user id.
     *
     * @param limit Optional maximum number of chats to return.
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The current user's chat metadata.
     */
    @Operation(
        summary = "Retrieves current user's chats with metadata",
        description = "Retrieves the authenticated user's chats, including only metadata and not messages.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Chats retrieved successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "404", description = "Authenticated user not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER') and @projectAuth.canAccessProject(authentication, #projectId)")
    fun getMyChats(
        @RequestParam @Min(1) limit: Int?,
        @RequestParam projectId: UUID,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GetChatsResponse {
        val request = GetChatsRequest(limit = limit, projectId = projectId)
        return chatService.getChatsForCurrentUser(jwt.subject, request)
    }

    @Operation(
        summary = "Retrieves a chat's messages",
        description = "Retrieves the n last messages of a specific chat. Not specifying the limit fetches all.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Messages retrieved successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getChatMessages(
        @PathVariable id: UUID,
        @RequestParam(required = false) @Min(1) limit: Int?,
    ): GetChatMessagesResponse {
        val request = GetChatMessagesRequest(limit = limit)
        return chatService.getChat(id, request)
    }

    /**
     * Retrieves messages for one authenticated-user chat.
     *
     * The chat id is looked up together with the current user's id, so foreign chats return the
     * same not-found response as missing chats.
     *
     * @param id Chat id to retrieve.
     * @param limit Optional maximum number of messages to return.
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The current user's chat messages.
     */
    @Operation(
        summary = "Retrieves current user's chat messages",
        description = "Retrieves messages for one chat owned by the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Messages retrieved successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "404", description = "Chat not found for authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/{id}")
    @PreAuthorize("hasRole('USER')")
    fun getMyChatMessages(
        @PathVariable id: UUID,
        @RequestParam(required = false) @Min(1) limit: Int?,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GetChatMessagesResponse {
        val request = GetChatMessagesRequest(limit = limit)
        return chatService.getChatForCurrentUser(jwt.subject, id, request)
    }

    @Operation(
        summary = "Initializes a new chat",
        description = "Creates a new chat and persists it in the db",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Chat successfully created"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun createChat(@Valid @RequestBody request: CreateChatRequest): CreateChatResponse {
        return chatService.createChat(request)
    }

    /**
     * Creates a chat owned by the authenticated user.
     *
     * The request body is intentionally empty because the owner is derived from the JWT subject.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The newly created chat id.
     */
    @Operation(
        summary = "Initializes a current-user chat",
        description = "Creates a new chat owned by the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Chat successfully created"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "404", description = "Authenticated user not found"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me")
    @PreAuthorize("hasRole('USER') and @projectAuth.canAccessProject(authentication, #request.projectId)")
    fun createMyChat(
        @Valid @RequestBody request: CreateMyChatRequest,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): CreateChatResponse {
        return chatService.createChatForCurrentUser(jwt.subject, request.projectId)
    }

    /**
     * Prompts the AI for a chat owned by the authenticated user.
     *
     * Ownership is verified before loading chat history or opening the AI stream.
     *
     * @param request Prompt payload containing the chat id and message text.
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return A server-sent event stream from the AI response.
     */
    @Operation(
        summary = "Prompts the AI for a current-user chat",
        description = "Adds a prompt to a chat owned by the authenticated user and streams the AI response.",
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
                                "data: {\"type\": \"tool_use\", \"name\": \"retrieve\", \"kind\": \"tool\"}",
                                "data: {\"type\": \"token\", \"content\": \"The main\"}",
                                "data: {\"type\": \"citation\", \"artifact_id\": \"artifact-1\"," +
                                    "\"start_line\": 12}",
                                "data: {\"type\": \"done\"}",
                                "data: {\"type\": \"error\", \"message\": \"LLM backend unreachable\"}",
                            ],
                        ),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "404", description = "Chat not found for authenticated user"),
            ApiResponse(responseCode = "500", description = "Internal server error"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/prompt", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasRole('USER')")
    suspend fun promptMyChat(
        @Valid @RequestBody request: PromptRequest,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): Flow<AiStreamMessage> {
        return chatService.promptForCurrentUser(jwt.subject, request)
    }
}
