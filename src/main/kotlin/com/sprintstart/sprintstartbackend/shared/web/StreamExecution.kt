package com.sprintstart.sprintstartbackend.shared.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import java.net.http.HttpResponse

/**
 * Executes an SSE / chunked streaming HTTP request, exposing the response as a [Flow].
 *
 * Obtained via [RequestBuilder.stream]. Never instantiated directly.
 *
 * The stream is consumed line-by-line on [Dispatchers.IO] via [flowOn], keeping the
 * collection side free to run on whichever dispatcher the caller prefers. Back-pressure
 * is handled naturally by the Flow contract — the producer only advances when the
 * collector is ready.
 *
 * ### SSE format
 * Expects the server to emit standard SSE lines: `data: <payload>`.
 * Lines not starting with `data:` (comments, blank keep-alives) are silently skipped.
 * Each matching line's payload is deserialized into [T] and emitted downstream.
 *
 * ### Error handling
 * A non-2xx status before the stream starts throws [WebClientException].
 * Deserialization errors per-chunk call [onChunkError] (default: log and skip),
 * so a single malformed chunk does not kill the whole stream.
 *
 * ### Usage
 * ```kotlin
 * webClient
 *     .post()
 *     .uri("https://api.example.com/chat/stream")
 *     .body(chatRequest)
 *     .stream()
 *     .perform<AiStreamChunk>()
 *     .collect { chunk -> println(chunk.token) }
 * ```
 */
class StreamExecution internal constructor(
    val builder: RequestBuilder,
) {
    /**
     * Opens the SSE stream and returns a cold [Flow] of deserialized [T] chunks.
     *
     * The flow is cold — the HTTP connection is only opened when collection begins,
     * and is closed automatically when the flow completes or is cancelled.
     *
     * @param terminationMarkers SSE `data:` payloads that signal the stream is done.
     *   Defaults to `[done]` (the standard OpenAI-style sentinel). Override as needed.
     * @param onChunkError Called when a chunk fails deserialization. Defaults to stderr logging.
     *   Return `true` to continue the stream, `false` to cancel it.
     */
    inline fun <reified T> perform(
        terminationMarkers: Set<String> = setOf("[DONE]"),
        crossinline onChunkError: (raw: String, error: Throwable) -> Boolean = { raw, err ->
            System.err.println("WebClient stream: failed to deserialize chunk '$raw': ${err.message}")
            true // skip and continue by default
        },
    ): Flow<T> = flow {
        val request = builder
            .buildHttpRequest()
            .let { original ->
                if (original.headers().firstValue("Accept").isEmpty) {
                    java.net.http.HttpRequest
                        .newBuilder(original, { _, _ -> true })
                        .header("Accept", "text/event-stream")
                        .build()
                } else {
                    original
                }
            }

        val response = builder.httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofLines())
            .await()

        if (response.statusCode() !in HTTP_SUCCESS_RANGE) {
            throw WebClientException(
                statusCode = response.statusCode(),
                body = "(streaming — body not buffered)",
                message = "Stream request to ${request.uri()} failed with status ${response.statusCode()}",
            )
        }

        response.body().use { lineStream ->
            @Suppress("LoopWithTooManyJumpStatements")
            for (line in lineStream) {
                if (!line.startsWith("data:")) continue

                val raw = line.removePrefix("data:").trim()

                if (raw in terminationMarkers) break

                try {
                    emit(builder.jsonParser.decodeFromString<T>(raw))
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    val shouldContinue = onChunkError(raw, e)
                    if (!shouldContinue) break
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
