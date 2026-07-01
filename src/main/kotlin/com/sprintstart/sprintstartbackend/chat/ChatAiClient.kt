package com.sprintstart.sprintstartbackend.chat

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.chat.models.exceptions.AiResponseException
import com.sprintstart.sprintstartbackend.chat.models.requests.AiGenerateChatTitleRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.AiPromptRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.AiGenerateChatTitleResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Component
import java.net.URI

/**
 * AI chat module HTTP wrapper.
 *
 * This is the *only* class in the `chat` module that knows about HTTP or URIs.
 * Everything above this layer works purely with domain types.
 *
 * Responsibilities:
 * - Build URIs from the configured base URL
 * - Map domain types onto [WebClient] calls
 * - Translate [WebClientException] into domain exceptions ([AiResponseException])
 * - Interpret SSE chunk semantics (done/token/error) from raw [AiStreamMessage]
 *
 * Not responsible for:
 * - Any HTTP mechanics (that's [WebClient])
 * - Any business logic (that's the service layer above)
 */
@Component
class ChatAiClient(
    private val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    /**
     * Fetches a generated title for a new chat session.
     *
     * @throws AiResponseException if the AI service returns an error.
     */
    suspend fun getChatTitle(request: AiGenerateChatTitleRequest): AiGenerateChatTitleResponse =
        try {
            webClient
                .post()
                .uri(uri("/api/v1/generate-title"))
                .body(request)
                .sync()
                .perform<AiGenerateChatTitleResponse>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw AiResponseException("Failed to generate chat title (HTTP ${e.statusCode}): ${e.body}")
        }

    /**
     * Opens an SSE stream for a chat prompt.
     *
     * The returned [Flow] is cold; the connection is not opened until collection begins.
     * The caller is responsible for collecting on an appropriate dispatcher / scope.
     *
     * Each emitted [AiStreamMessage] has already been filtered for type:
     * - `token`, `citation` and `tool_use` chunks pass through.
     * - `done` terminates the stream normally.
     * - `error` chunks terminate the stream with [AiResponseException].
     *
     * @throws AiResponseException if the AI service returns a non-2xx status at stream open,
     *   or if an `error` chunk arrives mid-stream.
     */
    fun streamPrompt(request: AiPromptRequest): Flow<AiStreamMessage> =
        webClient
            .post()
            .uri(uri("/api/v1/chat"))
            .body(request)
            .stream()
            .perform<AiStreamMessage>(
                terminationMarkers = setOf("[DONE]"),
                onChunkError = { raw, err ->
                    System.err.println("AiClient: skipping malformed SSE chunk '$raw': ${err.message}")
                    true
                },
            ).map { chunk ->
                when (chunk.type) {
                    "error" -> throw AiResponseException("AI responded with error: ${chunk.message}")
                    else -> chunk // token, citation, tool_use — pass through
                }
            }

    // ── URI helpers ───────────────────────────────────────────────────────────

    private fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
