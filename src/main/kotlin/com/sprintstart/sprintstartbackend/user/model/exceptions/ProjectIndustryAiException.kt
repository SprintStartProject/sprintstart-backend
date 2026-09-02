package com.sprintstart.sprintstartbackend.user.model.exceptions

/**
 * Thrown when the AI service fails to return a valid project industry evaluation.
 */
class ProjectIndustryAiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
