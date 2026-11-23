package com.sprintstart.sprintstartbackend.shared.web

import com.sprintstart.sprintstartbackend.shared.failure.recovering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.stream.Stream
import kotlin.streams.asSequence

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
 * A non-2xx status before the stream starts throws [WebClientException]. The response body is
 * captured with a bounded size so validation errors from upstream services remain diagnosable.
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
        openLines().use { lineStream ->
            val decode = { payload: String -> builder.jsonParser.decodeFromString<T>(payload) }
            val onError = { raw: String, error: Throwable -> onChunkError(raw, error) }
            for (raw in dataPayloads(lineStream, terminationMarkers)) {
                if (!emitDecoded(raw, decode, onError)) break
            }
        }
    }.flowOn(Dispatchers.IO)

    /** The payloads of the `data:` lines, up to the first termination marker. */
    @PublishedApi
    internal fun dataPayloads(lines: Stream<String>, terminationMarkers: Set<String>): Sequence<String> =
        lines
            .asSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .takeWhile { it !in terminationMarkers }

    /** Emits the decoded [raw] chunk; false when [onChunkError] asked to stop after a malformed one. */
    @PublishedApi
    internal suspend fun <T> FlowCollector<T>.emitDecoded(
        raw: String,
        decode: (String) -> T,
        onChunkError: (raw: String, error: Throwable) -> Boolean,
    ): Boolean {
        // A cancellation (e.g. a caller-imposed timeout, or `take(n)` completing) must abort the
        // stream, not be mistaken for a malformed chunk; `recovering` rethrows it.
        return recovering(fallback = { e -> onChunkError(raw, e) }) {
            emit(decode(raw))
            true
        }
    }

    @PublishedApi
    internal suspend fun openLines(): Stream<String> {
        val request = withEventStreamAccept(builder.buildHttpRequest())
        val response = builder.httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofLines())
            .await()

        if (response.statusCode() !in HTTP_SUCCESS_RANGE) {
            val errorBody = response.body().use { lines ->
                lines
                    .limit(100)
                    .toList()
                    .joinToString("\n")
                    .take(16_384)
            }
            throw WebClientException(
                statusCode = response.statusCode(),
                body = errorBody,
                message = "Stream request to ${request.uri()} failed with status " +
                    "${response.statusCode()}: $errorBody",
            )
        }
        return response.body()
    }

    private fun withEventStreamAccept(original: HttpRequest): HttpRequest {
        if (!original.headers().firstValue("Accept").isEmpty) return original
        return HttpRequest
            .newBuilder(original, { _, _ -> true })
            .header("Accept", "text/event-stream")
            .build()
    }
}
