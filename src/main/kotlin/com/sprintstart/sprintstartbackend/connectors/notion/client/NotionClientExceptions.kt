package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.shared.web.WebClientException

/**
 * Represents a sanitized Notion client failure safe to propagate beyond the HTTP client.
 *
 * The exception retains only classification and retry metadata; tokens and raw upstream response
 * bodies are deliberately excluded.
 */
internal sealed class NotionClientException(
    message: String,
    val requestContext: String,
    val httpStatus: Int?,
    val attempts: Int,
) : RuntimeException(message)

internal class NotionAuthenticationException(
    requestContext: String,
    attempts: Int = 1,
) : NotionClientException("Notion authentication failed while $requestContext", requestContext, 401, attempts)

internal class NotionAccessDeniedException(
    requestContext: String,
    attempts: Int = 1,
) : NotionClientException("Notion access was denied while $requestContext", requestContext, 403, attempts)

internal class NotionResourceNotFoundException(
    requestContext: String,
    attempts: Int = 1,
) : NotionClientException("Notion resource was not found while $requestContext", requestContext, 404, attempts)

internal class NotionExternalServiceException(
    requestContext: String,
    httpStatus: Int,
    attempts: Int,
    val retryExhausted: Boolean,
) : NotionClientException(
        "Notion request failed with status $httpStatus while $requestContext",
        requestContext,
        httpStatus,
        attempts,
    )

/** Represents an exhausted or non-retryable transport failure without retaining the cause. */
internal class NotionTransportException(
    requestContext: String,
    attempts: Int,
    val retryExhausted: Boolean,
) : NotionClientException("Notion transport failed while $requestContext", requestContext, null, attempts)

/** Represents malformed or internally inconsistent Notion response data. */
internal class NotionInvalidResponseException(
    requestContext: String,
    attempts: Int = 1,
) : NotionClientException("Notion returned an invalid response while $requestContext", requestContext, null, attempts)

/** Indicates that the shared throttle could not grant a permit within its wait budget. */
internal class NotionRequestDeferredException(
    requestContext: String,
) : NotionClientException("Notion request wait budget exceeded while $requestContext", requestContext, null, 0)

internal fun WebClientException.toSafeNotionException(
    requestContext: String,
    attempts: Int,
    retryExhausted: Boolean,
): NotionClientException {
    return when (statusCode) {
        401 -> NotionAuthenticationException(requestContext, attempts)
        403 -> NotionAccessDeniedException(requestContext, attempts)
        404 -> NotionResourceNotFoundException(requestContext, attempts)
        else -> NotionExternalServiceException(requestContext, statusCode, attempts, retryExhausted)
    }
}
