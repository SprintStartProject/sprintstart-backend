package com.sprintstart.sprintstartbackend.connectors.confluence.controller

import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceClientException
import com.sprintstart.sprintstartbackend.connectors.confluence.client.ConfluenceInvalidResponseException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionException
import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceIngestionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Maps sanitized Confluence domain failures to stable HTTP responses. */
@RestControllerAdvice
internal class ConfluenceExceptionHandler {
    @ExceptionHandler(ConfluenceConnectionException::class)
    fun handleConnectionFailure(exception: ConfluenceConnectionException): ResponseEntity<ConfluenceErrorResponse> {
        return ResponseEntity
            .status(exception.httpStatus)
            .body(ConfluenceErrorResponse(exception.message ?: CONNECTION_FAILURE_MESSAGE))
    }

    @ExceptionHandler(ConfluenceClientException::class)
    fun handleClientFailure(exception: ConfluenceClientException): ResponseEntity<ConfluenceErrorResponse> {
        val status = when (exception.httpStatus) {
            401 -> HttpStatus.UNAUTHORIZED
            403 -> HttpStatus.FORBIDDEN
            404 -> HttpStatus.NOT_FOUND
            else -> HttpStatus.BAD_GATEWAY
        }
        return ResponseEntity.status(status).body(ConfluenceErrorResponse(exception.message ?: CLIENT_FAILURE_MESSAGE))
    }

    @ExceptionHandler(ConfluenceInvalidResponseException::class)
    fun handleInvalidResponse(): ResponseEntity<ConfluenceErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ConfluenceErrorResponse(INVALID_RESPONSE_MESSAGE))
    }

    @ExceptionHandler(ConfluenceIngestionException::class)
    fun handleIngestionFailure(): ResponseEntity<ConfluenceErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ConfluenceErrorResponse(INGESTION_FAILURE_MESSAGE))
    }

    private companion object {
        const val CONNECTION_FAILURE_MESSAGE = "Confluence connection operation failed"
        const val CLIENT_FAILURE_MESSAGE = "Confluence service request failed"
        const val INVALID_RESPONSE_MESSAGE = "Confluence returned an invalid response"
        const val INGESTION_FAILURE_MESSAGE = "Confluence ingestion failed"
    }
}

internal data class ConfluenceErrorResponse(
    val message: String,
)
