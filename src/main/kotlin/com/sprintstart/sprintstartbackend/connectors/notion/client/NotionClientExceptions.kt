package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.shared.web.WebClientException

internal sealed class NotionClientException(
    message: String,
    val requestContext: String,
    val httpStatus: Int?,
    val attempts: Int,
) : RuntimeException(message)

internal class NotionAuthenticationException(requestContext: String, attempts: Int = 1) :
    NotionClientException("Notion authentication failed while $requestContext", requestContext, 401, attempts)

internal class NotionAccessDeniedException(requestContext: String, attempts: Int = 1) :
    NotionClientException("Notion access was denied while $requestContext", requestContext, 403, attempts)

internal class NotionResourceNotFoundException(requestContext: String, attempts: Int = 1) :
    NotionClientException("Notion resource was not found while $requestContext", requestContext, 404, attempts)

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

internal class NotionTransportException(
    requestContext: String,
    attempts: Int,
    val retryExhausted: Boolean,
) : NotionClientException("Notion transport failed while $requestContext", requestContext, null, attempts)

internal class NotionInvalidResponseException(requestContext: String, attempts: Int = 1) :
    NotionClientException("Notion returned an invalid response while $requestContext", requestContext, null, attempts)

internal class NotionRequestDeferredException(requestContext: String) :
    NotionClientException("Notion request wait budget exceeded while $requestContext", requestContext, null, 0)

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
