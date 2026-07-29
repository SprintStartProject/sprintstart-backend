package com.sprintstart.sprintstartbackend.connectors.jira.controller

import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraAuthException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceUnavailableException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * A centralized error-handling component for managing exceptions related to Jira operations.
 * This class intercepts exceptions thrown during interactions with Jira and translates them
 * into well-defined HTTP responses with appropriate status codes and error messages.
 */
@ControllerAdvice
internal class JiraExceptionHandler {
    /**
     * Handles exceptions of type `JiraAuthException` by returning a response with the corresponding
     * HTTP status code and an error body containing the exception message.
     *
     * @param ex The `JiraAuthException` instance containing details about the failed authentication
     * with the Jira instance.
     * @return A `ResponseEntity` object with the status code specified in the exception and an
     * `ErrorResponse` body containing the exception message.
     */
    @ExceptionHandler(JiraAuthException::class)
    fun handleJiraAuthFailed(ex: JiraAuthException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(ex.code)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type `JiraCredentialNotFoundException` by returning a response
     * with HTTP status `404 Not Found` and an error body containing the exception message.
     *
     * @param ex The `JiraCredentialNotFoundException` instance containing details about
     * the missing Jira credentials for a specific user.
     * @return A `ResponseEntity` object with status `404 Not Found` and an `ErrorResponse` body
     * containing the exception message.
     */
    @ExceptionHandler(JiraCredentialNotFoundException::class)
    fun handleCredentialsNotFound(ex: JiraCredentialNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type `JiraInstanceNotConnectedException` by returning a response
     * with HTTP status `404 Not Found` and an error body containing the exception message.
     *
     * @param ex The `JiraInstanceNotConnectedException` instance containing details about
     * the disconnected Jira instance.
     * @return A `ResponseEntity` object with status `404 Not Found` and an `ErrorResponse` body
     * containing the exception message.
     */
    @ExceptionHandler(JiraInstanceNotConnectedException::class)
    fun handleJiraInstanceNotConnected(ex: JiraInstanceNotConnectedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type `JiraInstanceUnavailableException` by returning a response
     * with HTTP status `502 Bad Gateway` and an error body containing the exception message.
     *
     * @param ex The exception instance containing details about the unavailable Jira instance.
     * @return A `ResponseEntity` object with status `502 Bad Gateway` and an `ErrorResponse` body.
     */
    @ExceptionHandler(JiraInstanceUnavailableException::class)
    fun handleInstanceUnavailable(ex: JiraInstanceUnavailableException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type `JiraResourceNotFoundException` by returning a response
     * with HTTP status `404 Not Found` and an error body containing the exception message.
     *
     * @param ex The exception instance containing details of the resource not found.
     * @return A `ResponseEntity` object with status `404 Not Found` and an `ErrorResponse` body.
     */
    @ExceptionHandler(JiraResourceNotFoundException::class)
    fun handleResourceNotFound(ex: JiraResourceNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type `JiraCredentialAlreadyExistsException` by returning a response
     * with HTTP status `400 Bad Request` and an error body containing the exception message.
     *
     * @param ex The `JiraCredentialAlreadyExistsException` instance containing details about
     * the duplicate Jira credential that caused the exception.
     * @return A `ResponseEntity` object with status `400 Bad Request` and an `ErrorResponse` body
     * containing the exception message.
     */
    @ExceptionHandler(JiraCredentialAlreadyExistsException::class)
    fun handleCredentialAlreadyExists(ex: JiraCredentialAlreadyExistsException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))
}

data class ErrorResponse(
    val message: String?,
)
