package com.sprintstart.sprintstartbackend.connectors.confluence.model.exception

import java.util.UUID

internal sealed class ConfluenceConnectionException(
    message: String,
    val httpStatus: Int,
) : RuntimeException(message)

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
) : ConfluenceConnectionException(
        message = "Confluence space $spaceId is already connected to project $projectId",
        httpStatus = 409,
    )

internal class ConfluenceConnectionConfigurationException(
    message: String,
    httpStatus: Int = 400,
) : ConfluenceConnectionException(message, httpStatus)
