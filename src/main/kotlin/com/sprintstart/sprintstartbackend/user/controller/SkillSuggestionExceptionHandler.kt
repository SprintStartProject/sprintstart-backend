package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.exceptions.SkillSuggestionAiException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Handles exceptions thrown during skill suggestion and maps them to appropriate HTTP responses.
 */
@ControllerAdvice
class SkillSuggestionExceptionHandler {
    /**
     * Handles exceptions of type [SkillSuggestionAiException] and maps them to
     * a standardized error response with a 502 BAD GATEWAY HTTP status code.
     *
     * @param ex The exception thrown by the AI client.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 502 (BAD GATEWAY).
     */
    @ExceptionHandler(SkillSuggestionAiException::class)
    fun handleSkillSuggestionFailed(ex: SkillSuggestionAiException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(ex.message))
}
