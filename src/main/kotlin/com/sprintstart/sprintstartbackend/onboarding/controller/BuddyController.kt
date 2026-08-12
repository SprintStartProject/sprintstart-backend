package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.SendBuddyMessageRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddySuggestionResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddySuggestionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Exposes the hire's persistent onboarding buddy: one continuous, repo-grounded companion
 * conversation per user.
 */
@RestController
@RequestMapping("/api/v1/onboarding/me/buddy")
@Tag(name = "Onboarding - Buddy", description = "A hire's persistent onboarding companion")
class BuddyController(
    private val buddyService: BuddyService,
    private val buddyActionService: BuddyActionService,
    private val buddySuggestionService: BuddySuggestionService,
) {
    @Operation(
        summary = "Get the current user's buddy conversation",
        description = "Returns the authenticated user's buddy conversation so far, oldest first.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Conversation returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this conversation"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/messages")
    @PreAuthorize("hasRole('USER')")
    fun getMessagesForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): List<BuddyMessageResponse> = buddyService.getMessagesForMe(jwt.subject)

    @Operation(
        summary = "Things this hire could usefully ask",
        description = "Chips for the composer: short questions drawn from the tools actually mounted for this " +
            "hire, so a chip is never offered for something their buddy cannot answer. Each one is put in the " +
            "composer for the hire to send — the client must not send it for them. Calls no model, so a surface " +
            "can show them before a greeting has arrived.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Suggestions returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this conversation"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/suggestions")
    @PreAuthorize("hasRole('USER')")
    fun getSuggestionsForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): List<BuddySuggestionResponse> = buddySuggestionService.forMe(jwt.subject)

    /**
     * Opens a buddy visit, streaming the greeting as the mentor writes it.
     *
     * ⚠️ **Opening cost about thirty seconds, and the cause was ordering rather than model speed.**
     * The AI wrote a private memory note of up to 200 words *before* the greeting, so the hire waited
     * on output they never see. The greeting is written first now and streamed as it arrives — same
     * single model call, same stored result, first word in about a second.
     *
     * There is deliberately no non-streaming twin. One existed while the client was being switched
     * over; keeping it afterwards would have been a second way to open a visit that nothing called.
     */
    @Operation(
        summary = "Open a buddy visit (streaming)",
        description = "The same visit as `POST /open` — the previous visit folded into the mentor's durable " +
            "memory, a proactive greeting grounded in the hire's state, no transcript replay — with the greeting " +
            "streamed as it is written instead of arriving whole. Opening twice without the hire saying anything " +
            "is the same visit: the greeting already there is replayed and no model is called. A visit whose " +
            "stream breaks keeps whatever the hire already read.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Stream started successfully, the greeting will now arrive token by token",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(
                            examples = [
                                "data: {\"type\": \"token\", \"content\": \"Welcome back, Sam!\"}",
                                "data: {\"type\": \"opening_action\", \"label\": \"Find me a task\", " +
                                    "\"question\": \"What should I work on?\"}",
                                "data: {\"type\": \"done\"}",
                            ],
                        ),
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this conversation"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/open/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasRole('USER')")
    suspend fun streamOpenForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): Flow<BuddyStreamEvent> = buddyService.streamOpenForMe(jwt.subject)

    @Operation(
        summary = "Send a message to the buddy",
        description = "Adds the message to the user's ongoing buddy session and streams a grounded reply.",
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
                                "data: {\"type\": \"token\", \"content\": \"No question is\"}",
                                "data: {\"type\": \"citation\", \"artifact_id\": \"artifact-1\", \"start_line\": 12}",
                                "data: {\"type\": \"done\"}",
                                "data: {\"type\": \"error\", \"message\": \"LLM backend unreachable\"}",
                            ],
                        ),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this conversation"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/messages", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasRole('USER')")
    suspend fun sendMessageForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: SendBuddyMessageRequest,
    ): Flow<BuddyStreamEvent> = buddyService.sendMessageForMe(jwt.subject, request.content)

    @Operation(
        summary = "Confirm a buddy-proposed action",
        description = "Runs an action the buddy proposed, on the hire's explicit confirmation — start Task 0, " +
            "open the task packet, log buddy contact, or flag a question to the PM. The action is re-scoped to " +
            "the caller server-side. Returns a single line to relay; a handled failure is `ok = false`, not an error.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Action attempted; see the outcome"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/actions")
    @PreAuthorize("hasRole('USER')")
    suspend fun performAction(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: BuddyActionRequest,
    ): BuddyActionResponse = buddyActionService.perform(request, jwt)
}
