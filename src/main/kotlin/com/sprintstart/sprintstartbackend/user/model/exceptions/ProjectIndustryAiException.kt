package com.sprintstart.sprintstartbackend.user.model.exceptions

/**
 * Thrown when the AI service fails to return a valid project industry evaluation.
 *
 * Preserves the upstream [statusCode] and raw response [body] so callers and exception
 * handlers can distinguish transient outages from client errors.
 */
class ProjectIndustryAiException(
    val statusCode: Int,
    val body: String,
    message: String,
) : RuntimeException(message)
