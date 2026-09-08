package com.sprintstart.sprintstartbackend.connectors.confluence.client

import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import java.util.Base64

internal fun WebClientException.toSafeConfluenceException(
    requestContext: String,
    attempts: Int = 1,
    retryExhausted: Boolean = false,
): RuntimeException {
    return when (statusCode) {
        401 -> ConfluenceAuthenticationException(requestContext, attempts)
        403 -> ConfluenceAccessDeniedException(requestContext, attempts)
        404 -> ConfluenceResourceNotFoundException(requestContext, attempts)
        else -> ConfluenceExternalServiceException(statusCode, requestContext, attempts, retryExhausted)
    }
}

internal fun ConfluenceClientCredentials.basicAuthorizationHeader(): String {
    val rawCredentials = "${email.trim()}:${apiToken.trim()}"
    val encodedCredentials = Base64.getEncoder().encodeToString(rawCredentials.toByteArray(Charsets.UTF_8))
    return "Basic $encodedCredentials"
}
