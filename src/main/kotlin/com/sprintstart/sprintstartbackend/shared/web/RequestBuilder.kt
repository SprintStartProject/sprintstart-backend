package com.sprintstart.sprintstartbackend.shared.web

import java.net.URI
import java.net.http.HttpRequest.BodyPublishers.noBody
import java.net.http.HttpRequest.BodyPublishers.ofString

/**
 * Immutable accumulator for HTTP request parameters, constructed via [WebClient].
 *
 * This class is intentionally decoupled from execution. It only holds *what* to send.
 * The *how* (sync suspend vs. streaming) is decided by calling [sync] or [stream],
 * which hand the finished [RequestBuilder] off to the appropriate execution context.
 *
 * All `with*` functions return a new copy of the builder, making the chain safe to
 * branch and reuse if needed.
 *
 * Typical usage:
 * ```kotlin
 * webClient
 *     .post()
 *     .uri(URI.create("https://api.example.com/chat"))
 *     .header("Authorization", "Bearer $token")
 *     .body(myRequest)
 *     .sync()
 *     .perform<MyResponse>()
 * ```
 */
class RequestBuilder(
    val method: String,
    val httpClient: java.net.http.HttpClient,
    val jsonParser: kotlinx.serialization.json.Json,
    val uri: URI? = null,
    val headers: Map<String, String> = emptyMap(),
    val rawBody: String? = null,
) {
    // ── URI ──────────────────────────────────────────────────────────────────

    fun uri(uri: URI): RequestBuilder = copy(uri = uri)

    fun uri(uri: String): RequestBuilder = copy(uri = URI.create(uri))

    // ── Headers ──────────────────────────────────────────────────────────────

    fun header(key: String, value: String): RequestBuilder =
        copy(headers = headers + (key to value))

    fun headers(vararg pairs: Pair<String, String>): RequestBuilder =
        copy(headers = headers + pairs.toMap())

    fun headers(map: Map<String, String>): RequestBuilder =
        copy(headers = headers + map)

    // ── Body ─────────────────────────────────────────────────────────────────

    /**
     * Serializes [body] to JSON and attaches it as the request body.
     * Also sets `Content-Type: application/json` automatically.
     */
    inline fun <reified T> body(body: T): RequestBuilder {
        val json = jsonParser.encodeToString(body)
        return copy(
            rawBody = json,
            headers = headers + ("Content-Type" to "application/json"),
        )
    }

    // ── Execution context selection ───────────────────────────────────────────

    /**
     * Returns a [SyncExecution] context for a standard request/response cycle.
     * Call `.perform<ResponseType>()` on the result to fire the request.
     */
    fun sync(): SyncExecution = SyncExecution(this)

    /**
     * Returns a [StreamExecution] context for SSE / chunked streaming responses.
     * Call `.perform<ChunkType>()` on the result to open the stream as a [kotlinx.coroutines.flow.Flow].
     */
    fun stream(): StreamExecution = StreamExecution(this)

    // ── Internal copy helper ──────────────────────────────────────────────────

    @PublishedApi
    internal fun copy(
        method: String = this.method,
        uri: URI? = this.uri,
        headers: Map<String, String> = this.headers,
        rawBody: String? = this.rawBody,
    ): RequestBuilder = RequestBuilder(
        method = method,
        httpClient = this.httpClient,
        jsonParser = this.jsonParser,
        uri = uri,
        headers = headers,
        rawBody = rawBody,
    )

    @PublishedApi
    internal fun buildHttpRequest(): java.net.http.HttpRequest {
        requireNotNull(uri) { "URI must be set before performing a request" }

        val bodyPublisher = if (rawBody != null) {
            ofString(rawBody)
        } else {
            noBody()
        }

        return java.net.http.HttpRequest
            .newBuilder()
            .uri(uri)
            .method(method.uppercase(), bodyPublisher)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
    }
}
