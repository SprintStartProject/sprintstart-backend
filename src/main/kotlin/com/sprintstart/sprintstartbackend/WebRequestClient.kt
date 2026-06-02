package com.sprintstart.sprintstartbackend

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * A lightweight HTTP client for executing synchronous REST API requests in enterprise applications.
 *
 * This class wraps the Java [HttpClient] and automates JSON serialization and
 * deserialization using `kotlinx.serialization`.
 *
 * *Note on Concurrency:* This client bridges asynchronous coroutine-like mechanics using [runBlocking],
 * making operations synchronous and blocking. For high-throughput reactive architectures, consider
 * refactoring these to native `suspend` functions instead, or implement custom traffic clients.
 *
 * @see com.sprintstart.sprintstartbackend.chat.AiWebRequestClient
 */
open class WebRequestClient {
    /**
     * The central JSON parser configured for enterprise API resilience.
     * Configured to ignore unknown JSON properties to prevent breaking changes when APIs evolve.
     */
    val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * Executes a synchronous **GET** request and deserializes the response body.
     *
     * @param ResponseType The expected target data type of the response.
     * @param uri The target [URI] of the endpoint.
     * @return The deserialized instance of [ResponseType].
     * @throws java.io.IOException If an I/O error occurs when sending or receiving.
     * @throws InterruptedException If the operation is interrupted.
     */
    inline fun <reified ResponseType> get(uri: URI): ResponseType {
        return request(uri, method = "GET")
    }

    /**
     * Executes a synchronous **POST** request with the provided payload serialized as JSON.
     *
     * @param PayloadType The type of the object being sent in the request body.
     * @param ResponseType The expected target data type of the response.
     * @param uri The target [URI] of the endpoint.
     * @param body The object to be serialized into the request body.
     * @return The deserialized instance of [ResponseType].
     * @throws kotlinx.serialization.SerializationException If the [body] cannot be serialized to JSON.
     */
    inline fun <reified PayloadType, reified ResponseType> post(
        uri: URI,
        body: PayloadType,
    ): ResponseType {
        val jsonPayload = jsonParser.encodeToString(body)
        return request(uri, method = "POST", payload = jsonPayload)
    }

    /**
     * Executes a synchronous **PUT** request with the provided payload serialized as JSON.
     *
     * @param PayloadType The type of the object being sent in the request body.
     * @param ResponseType The expected target data type of the response.
     * @param uri The target [URI] of the endpoint.
     * @param body The object containing updated data to be serialized into the request body.
     * @return The deserialized instance of [ResponseType].
     * @throws kotlinx.serialization.SerializationException If the [body] cannot be serialized to JSON.
     */
    inline fun <reified PayloadType, reified ResponseType> put(
        uri: URI,
        body: PayloadType,
    ): ResponseType {
        val jsonPayload = jsonParser.encodeToString(body)
        return request(uri, method = "PUT", payload = jsonPayload)
    }

    /**
     * Executes a synchronous **DELETE** request, optionally including a JSON payload.
     *
     * @param PayloadType The type of the object being sent in the request body.
     * @param ResponseType The expected target data type of the response.
     * @param uri The target [URI] of the endpoint.
     * @param body The object to be serialized into the delete request body.
     * @return The deserialized instance of [ResponseType].
     */
    inline fun <reified PayloadType, reified ResponseType> delete(
        uri: URI,
        body: PayloadType,
    ): ResponseType {
        val jsonPayload = jsonParser.encodeToString(body)
        return request(uri, method = "DELETE", payload = jsonPayload)
    }

    /**
     * Internal core method that orchestrates the underlying HTTP request and response flow.
     *
     * Marked with [@PublishedApi] because it is internal but called from public inline functions
     * to support [reified] type parameters.
     *
     * @param T The expected type for final JSON deserialization.
     * @param uri The target [URI].
     * @param method The HTTP verb to use (e.g., "GET", "POST"). Defaults to "GET".
     * @param payload The optional raw JSON string payload to include in the request body.
     * @return The deserialized instance of [T].
     */
    @PublishedApi
    internal inline fun <reified T> request(
        uri: URI,
        method: String = "GET",
        payload: String? = null,
    ): T {
        var responseData: String

        runBlocking {
            val client = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build()

            val requestBuilder = HttpRequest
                .newBuilder()
                .uri(uri)

            // Set up the body publisher if a payload exists
            val bodyPublisher = if (payload != null) {
                requestBuilder.header("Content-Type", "application/json")
                HttpRequest.BodyPublishers.ofString(payload)
            } else {
                HttpRequest.BodyPublishers.noBody()
            }

            // Inject the HTTP method
            requestBuilder.method(method.uppercase(), bodyPublisher)

            val request = requestBuilder.build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            responseData = response.body()
        }

        return this.jsonParser.decodeFromString<T>(responseData)
    }
}
