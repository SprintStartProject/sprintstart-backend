package com.sprintstart.sprintstartbackend.shared.web

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.net.http.HttpClient

@Suppress("MagicNumber")
val HTTP_SUCCESS_RANGE = 200..299

/**
 * Primary entry point for all outbound HTTP traffic in the application.
 *
 * [WebClient] is a thin factory — it constructs [RequestBuilder] instances preloaded
 * with the shared infrastructure ([HttpClient], [Json]) and the desired HTTP method.
 * All further configuration (URI, headers, body, execution mode) happens on the builder.
 *
 * Inject this bean into any Spring component or module-level wrapper that needs to
 * make outbound requests. Never construct it manually.
 *
 * ### Example
 * ```kotlin
 * val body: MyResponse = webClient
 *     .post()
 *     .uri("https://api.example.com/resource")
 *     .header("Authorization", "Bearer $token")
 *     .body(myRequest)
 *     .sync()
 *     .perform<MyResponse>()
 * ```
 *
 * ### Module wrappers
 * For each bounded context (chat, uploads, connectors, ...) create a thin wrapper that
 * takes [WebClient] as a constructor dependency and exposes domain-typed suspend
 * functions. The wrapper owns URI construction and request/response mapping;
 * [WebClient] owns transport only. See [com.sprintstart.sprintstartbackend.chat.ChatAiClient]
 * for a reference implementation.
 */
@Component
class WebClient(
    private val httpClient: HttpClient,
    private val jsonParser: Json,
) {
    fun get(): RequestBuilder = builder("GET")

    fun post(): RequestBuilder = builder("POST")

    fun put(): RequestBuilder = builder("PUT")

    fun patch(): RequestBuilder = builder("PATCH")

    fun delete(): RequestBuilder = builder("DELETE")

    private fun builder(method: String) = RequestBuilder(
        method = method,
        httpClient = httpClient,
        jsonParser = jsonParser,
    )
}
