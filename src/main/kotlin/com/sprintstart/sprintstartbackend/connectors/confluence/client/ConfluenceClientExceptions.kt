package com.sprintstart.sprintstartbackend.connectors.confluence.client

/**
 * Base type for terminal Confluence HTTP failures with sanitized request context.
 *
 * Upstream response bodies and credentials are intentionally not retained in this exception hierarchy.
 */
internal sealed class ConfluenceClientException(
    message: String,
    val httpStatus: Int,
    val requestContext: String,
) : RuntimeException(message)

internal class ConfluenceAuthenticationException(
    requestContext: String,
) : ConfluenceClientException(
        message = "Confluence authentication failed while $requestContext",
        httpStatus = 401,
        requestContext = requestContext,
    )

internal class ConfluenceAccessDeniedException(
    requestContext: String,
) : ConfluenceClientException(
        message = "Confluence access was denied while $requestContext",
        httpStatus = 403,
        requestContext = requestContext,
    )

internal class ConfluenceResourceNotFoundException(
    requestContext: String,
) : ConfluenceClientException(
        message = "Confluence resource was not found while $requestContext",
        httpStatus = 404,
        requestContext = requestContext,
    )

internal class ConfluenceExternalServiceException(
    httpStatus: Int,
    requestContext: String,
) : ConfluenceClientException(
        message = "Confluence request failed with status $httpStatus while $requestContext",
        httpStatus = httpStatus,
        requestContext = requestContext,
    )

/** Represents a malformed or internally inconsistent Confluence response without retaining upstream data. */
internal class ConfluenceInvalidResponseException(
    val requestContext: String,
) : RuntimeException("Confluence returned an invalid response while $requestContext")
