package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.exceptions.ProjectIndustryAiException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Handles exceptions thrown during project industry evaluation and maps them
 * to appropriate HTTP responses with an [ErrorResponse] body.
 */
@ControllerAdvice
class ProjectIndustryExceptionHandler {
    /**
     * Handles exceptions of type [ProjectIndustryAiException] and maps them to
     * a standardized error response with a 502 BAD GATEWAY HTTP status code.
     *
     * @param ex The exception thrown by the AI client.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 502 (BAD GATEWAY).
     */
    @ExceptionHandler(ProjectIndustryAiException::class)
    fun handleIndustryEvaluationFailed(ex: ProjectIndustryAiException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(ex.message))
}

data class ErrorResponse(
    val message: String?,
)
