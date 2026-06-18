package com.sprintstart.sprintstartbackend.shared.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.http.HttpResponse

/**
 * Executes a standard (non-streaming) HTTP request as a coroutine suspend function.
 *
 * Obtained via [RequestBuilder.sync]. Never instantiated directly.
 *
 * The underlying [java.net.http.HttpClient.send] call is dispatched on [Dispatchers.IO]
 * so it never blocks a coroutine thread. There is no `runBlocking` anywhere in this path.
 *
 * ### Error handling
 * Non-2xx responses throw a [WebClientException] carrying the status code and raw body,
 * letting callers decide how to handle domain-level errors without coupling transport to
 * business logic.
 *
 * ### Usage
 * ```kotlin
 * val response: MyResponse = webClient
 *     .get()
 *     .uri("https://api.example.com/users/1")
 *     .sync()
 *     .perform<MyResponse>()
 * ```
 */
class SyncExecution(
    val builder: RequestBuilder,
) {
    /**
     * Fires the request and deserializes the response body into [T].
     *
     * @throws WebClientException if the server returns a non-2xx status.
     * @throws kotlinx.serialization.SerializationException if the body cannot be deserialized.
     */
    suspend inline fun <reified T> perform(): T {
        val request = builder.buildHttpRequest()

        val response = withContext(Dispatchers.IO) {
            builder.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in HTTP_SUCCESS_RANGE) {
            throw WebClientException(
                statusCode = response.statusCode(),
                body = response.body(),
                message = "Request to ${request.uri()} failed with status ${response.statusCode()}",
            )
        }

        return builder.jsonParser.decodeFromString<T>(response.body())
    }

    /**
     * Fires the request and returns the raw response body as a [String], skipping deserialization.
     * Useful for debugging or when the response is not JSON.
     *
     * @throws WebClientException if the server returns a non-2xx status.
     */
    suspend fun performRaw(): String {
        val request = builder.buildHttpRequest()

        val response = withContext(Dispatchers.IO) {
            builder.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in HTTP_SUCCESS_RANGE) {
            throw WebClientException(
                statusCode = response.statusCode(),
                body = response.body(),
                message = "Request to ${request.uri()} failed with status ${response.statusCode()}",
            )
        }

        return response.body()
    }
}
