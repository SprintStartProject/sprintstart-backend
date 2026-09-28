package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClientException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPersistenceException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
internal class NotionExceptionHandler {
    @ExceptionHandler(NotionPersistenceException::class)
    fun handlePersistenceException(
        exception: NotionPersistenceException,
    ): ResponseEntity<NotionErrorResponse> {
        return ResponseEntity
            .status(exception.httpStatus)
            .body(
                NotionErrorResponse(
                    exception.message ?: PERSISTENCE_ERROR_MESSAGE,
                ),
            )
    }

    @ExceptionHandler(NotionClientException::class)
    fun handleClientException(
        exception: NotionClientException,
    ): ResponseEntity<NotionErrorResponse> {
        val status = when (exception.httpStatus) {
            HttpStatus.UNAUTHORIZED.value() -> HttpStatus.UNAUTHORIZED
            HttpStatus.FORBIDDEN.value() -> HttpStatus.FORBIDDEN
            HttpStatus.NOT_FOUND.value() -> HttpStatus.NOT_FOUND
            else -> HttpStatus.BAD_GATEWAY
        }
        return ResponseEntity
            .status(status)
            .body(
                NotionErrorResponse(
                    exception.message ?: CLIENT_ERROR_MESSAGE,
                ),
            )
    }

    private companion object {
        const val PERSISTENCE_ERROR_MESSAGE = "Notion persistence operation failed"
        const val CLIENT_ERROR_MESSAGE = "Notion service request failed"
    }
}

internal data class NotionErrorResponse(
    val message: String,
)
