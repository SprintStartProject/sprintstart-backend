package com.sprintstart.sprintstartbackend.connectors.confluence.model.exception

import java.util.UUID

internal sealed class ConfluenceConnectionException(
    message: String,
    val httpStatus: Int,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal class ConfluenceProjectAccessDeniedException(
    projectId: UUID,
) : ConfluenceConnectionException(
        message = "Access to project $projectId is denied",
        httpStatus = 403,
    )

internal class ConfluenceConnectionNotFoundException(
    connectionId: UUID,
    projectId: UUID,
) : ConfluenceConnectionException(
        message = "Confluence connection $connectionId was not found in project $projectId",
        httpStatus = 404,
    )

internal class ConfluenceConnectionAlreadyExistsException(
    projectId: UUID,
    spaceId: String,
    cause: Throwable? = null,
) : ConfluenceConnectionException(
        message = "Confluence space $spaceId is already connected to project $projectId",
        httpStatus = 409,
        cause = cause,
    )

internal class ConfluenceConnectionConfigurationException(
    message: String,
    httpStatus: Int = 400,
    cause: Throwable? = null,
) : ConfluenceConnectionException(message, httpStatus, cause)

internal class ConfluenceCredentialNotFoundException(
    credentialName: String,
) : ConfluenceConnectionException(
        message = "Atlassian credential '$credentialName' was not found",
        httpStatus = 404,
    )

internal class ConfluenceConnectionNotEnabledException(
    val name: String,
) : RuntimeException("Specified Confluence connection '$name' is not enabled")
