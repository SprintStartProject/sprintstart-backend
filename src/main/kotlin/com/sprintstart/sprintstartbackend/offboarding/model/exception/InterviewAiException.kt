package com.sprintstart.sprintstartbackend.offboarding.model.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** The AI service could not plan the interview; the risk map itself is unaffected. */
@ResponseStatus(HttpStatus.BAD_GATEWAY, reason = "The AI service could not plan the interview")
class InterviewAiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
