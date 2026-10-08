package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionClientException
import com.sprintstart.sprintstartbackend.connectors.notion.model.exception.NotionPersistenceException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Maps sanitized Notion domain failures to stable HTTP responses.
 *
 * Upstream Notion 401/403 responses become 422 so clients do not mistake a rejected PAT for an
 * expired SprintStart session. Other remote failures are exposed as gateway errors.
 */
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
                    PERSISTENCE_ERROR_CODE,
                ),
            )
    }

    @ExceptionHandler(NotionClientException::class)
    fun handleClientException(
        exception: NotionClientException,
    ): ResponseEntity<NotionErrorResponse> {
        val status = when (exception.httpStatus) {
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.FORBIDDEN.value(),
            -> HttpStatus.UNPROCESSABLE_ENTITY
            HttpStatus.NOT_FOUND.value() -> HttpStatus.NOT_FOUND
            else -> HttpStatus.BAD_GATEWAY
        }
        val code = when (exception.httpStatus) {
            HttpStatus.UNAUTHORIZED.value() -> AUTHENTICATION_ERROR_CODE
            HttpStatus.FORBIDDEN.value() -> ACCESS_DENIED_ERROR_CODE
            HttpStatus.NOT_FOUND.value() -> RESOURCE_NOT_FOUND_ERROR_CODE
            else -> UPSTREAM_ERROR_CODE
        }
        return ResponseEntity
            .status(status)
            .body(
                NotionErrorResponse(
                    exception.message ?: CLIENT_ERROR_MESSAGE,
                    code,
                ),
            )
    }

    private companion object {
        const val PERSISTENCE_ERROR_MESSAGE = "Notion persistence operation failed"
        const val CLIENT_ERROR_MESSAGE = "Notion service request failed"
        const val PERSISTENCE_ERROR_CODE = "NOTION_PERSISTENCE_ERROR"
        const val AUTHENTICATION_ERROR_CODE = "NOTION_AUTHENTICATION_FAILED"
        const val ACCESS_DENIED_ERROR_CODE = "NOTION_ACCESS_DENIED"
        const val RESOURCE_NOT_FOUND_ERROR_CODE = "NOTION_RESOURCE_NOT_FOUND"
        const val UPSTREAM_ERROR_CODE = "NOTION_UPSTREAM_ERROR"
    }
}

internal data class NotionErrorResponse(
    val message: String,
    val code: String,
)
