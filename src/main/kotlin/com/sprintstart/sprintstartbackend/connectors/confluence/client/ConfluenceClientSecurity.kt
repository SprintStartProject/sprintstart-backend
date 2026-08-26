package com.sprintstart.sprintstartbackend.connectors.confluence.client

import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import java.util.Base64

internal fun WebClientException.toSafeConfluenceException(requestContext: String): RuntimeException {
    return when (statusCode) {
        401 -> ConfluenceAuthenticationException(requestContext)
        403 -> ConfluenceAccessDeniedException(requestContext)
        404 -> ConfluenceResourceNotFoundException(requestContext)
        else -> ConfluenceExternalServiceException(statusCode, requestContext)
    }
}

internal fun ConfluenceClientCredentials.basicAuthorizationHeader(): String {
    val rawCredentials = "${email.trim()}:${apiToken.trim()}"
    val encodedCredentials = Base64.getEncoder().encodeToString(rawCredentials.toByteArray(Charsets.UTF_8))
    return "Basic $encodedCredentials"
}
