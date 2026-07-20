package com.sprintstart.sprintstartbackend.connectors.overview.controller

import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.ConnectorNotFoundException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Handles exceptions thrown during the operation of the application and maps them
 * to appropriate HTTP responses.
 *
 * This class is annotated with `@ControllerAdvice` to centralize exception handling
 * across the application. Each exception handler method catches specific exceptions
 * and returns a meaningful error response with an appropriate HTTP status code.
 */
@ControllerAdvice
class ConnectorOverviewExceptionHandler {
    /**
     * Handles exceptions of type [ConnectorNotFoundException] and converts them into a standardized error response
     * with a 404 NOT FOUND HTTP status code.
     *
     * @param ex The exception containing details like the specific message.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 404 (NOT FOUND).
     */
    @ExceptionHandler(ConnectorNotFoundException::class)
    fun handleConnectorNotFound(ex: ConnectorNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type [ConstraintViolationException] and converts them into a standardized error response
     * with a 400 BAD REQUEST HTTP status code.
     *
     * These exceptions are thrown by jakarta, not by our backend, when the api value validation fails. Intercepting it
     * here prevents it from becomming an INTERNAL_SERVER_ERROR, and clearly states to the caller it was their fault.
     *
     * @param ex The exception containing details like the specific message.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 404 (NOT FOUND).
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))
}

data class ErrorResponse(
    val message: String?,
)
