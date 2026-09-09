package com.sprintstart.sprintstartbackend.shared.web

/**
 * Thrown when an HTTP response carries a non-2xx status code.
 *
 * This is a *transport-level* exception. Module wrappers (e.g. [com.sprintstart.sprintstartbackend.chat.ChatAiClient])
 * should catch this and rethrow domain-specific exceptions where appropriate,
 * rather than letting [WebClientException] leak into business logic.
 *
 * @param statusCode The HTTP status code returned by the server.
 * @param body The raw response body. Streaming responses capture a bounded prefix for diagnostics.
 */
class WebClientException(
    val statusCode: Int,
    val body: String,
    message: String,
) : RuntimeException(message)
