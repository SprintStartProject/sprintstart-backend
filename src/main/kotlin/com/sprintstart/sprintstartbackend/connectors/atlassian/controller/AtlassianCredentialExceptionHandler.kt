package com.sprintstart.sprintstartbackend.connectors.atlassian.controller

import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * A centralized error-handling component for managing exceptions related to the shared Atlassian
 * credential store. Registered as a global `@ControllerAdvice`, so it also handles these exceptions
 * when they surface from the Jira and Confluence connector endpoints.
 */
@ControllerAdvice
internal class AtlassianCredentialExceptionHandler {
    /**
     * Handles exceptions of type `AtlassianCredentialNotFoundException` by returning a response
     * with HTTP status `404 Not Found` and an error body containing the exception message.
     *
     * @param ex The `AtlassianCredentialNotFoundException` instance containing details about
     * the missing Atlassian credential for a specific user.
     * @return A `ResponseEntity` object with status `404 Not Found` and an `ErrorResponse` body
     * containing the exception message.
     */
    @ExceptionHandler(AtlassianCredentialNotFoundException::class)
    fun handleCredentialsNotFound(ex: AtlassianCredentialNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type `AtlassianCredentialAlreadyExistsException` by returning a response
     * with HTTP status `400 Bad Request` and an error body containing the exception message.
     *
     * @param ex The `AtlassianCredentialAlreadyExistsException` instance containing details about
     * the duplicate Atlassian credential that caused the exception.
     * @return A `ResponseEntity` object with status `400 Bad Request` and an `ErrorResponse` body
     * containing the exception message.
     */
    @ExceptionHandler(AtlassianCredentialAlreadyExistsException::class)
    fun handleCredentialAlreadyExists(ex: AtlassianCredentialAlreadyExistsException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))
}

data class ErrorResponse(
    val message: String?,
)
