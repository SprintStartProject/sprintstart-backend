package com.sprintstart.sprintstartbackend.connectors.confluence.client

/**
 * Base type for terminal Confluence HTTP failures with sanitized request context.
 *
 * Upstream response bodies and credentials are intentionally not retained in this exception hierarchy.
 */
internal sealed class ConfluenceClientException(
    message: String,
    val httpStatus: Int?,
    val requestContext: String,
    val attempts: Int,
) : RuntimeException(message)

internal class ConfluenceAuthenticationException(
    requestContext: String,
    attempts: Int = 1,
) : ConfluenceClientException(
        message = "Confluence authentication failed while $requestContext",
        httpStatus = 401,
        requestContext = requestContext,
        attempts = attempts,
    )

internal class ConfluenceAccessDeniedException(
    requestContext: String,
    attempts: Int = 1,
) : ConfluenceClientException(
        message = "Confluence access was denied while $requestContext",
        httpStatus = 403,
        requestContext = requestContext,
        attempts = attempts,
    )

internal class ConfluenceResourceNotFoundException(
    requestContext: String,
    attempts: Int = 1,
) : ConfluenceClientException(
        message = "Confluence resource was not found while $requestContext",
        httpStatus = 404,
        requestContext = requestContext,
        attempts = attempts,
    )

internal class ConfluenceExternalServiceException(
    httpStatus: Int,
    requestContext: String,
    attempts: Int = 1,
    val retryExhausted: Boolean = false,
) : ConfluenceClientException(
        message = "Confluence request failed with status $httpStatus while $requestContext",
        httpStatus = httpStatus,
        requestContext = requestContext,
        attempts = attempts,
    )

/** Represents a sanitized transport failure without retaining the upstream exception. */
internal class ConfluenceTransportException(
    requestContext: String,
    attempts: Int,
    val retryExhausted: Boolean,
) : ConfluenceClientException(
        message = "Confluence transport failed while $requestContext",
        httpStatus = null,
        requestContext = requestContext,
        attempts = attempts,
    )

/** Represents a malformed or internally inconsistent Confluence response without retaining upstream data. */
internal class ConfluenceInvalidResponseException(
    val requestContext: String,
) : RuntimeException("Confluence returned an invalid response while $requestContext")
