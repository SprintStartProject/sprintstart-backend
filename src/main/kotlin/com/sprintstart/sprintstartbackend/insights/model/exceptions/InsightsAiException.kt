package com.sprintstart.sprintstartbackend.insights.model.exceptions

/**
 * Thrown when the AI service fails to deliver recurring-question groups.
 *
 * Wraps transport-level failures from the outbound AI call so the insights module does not leak
 * HTTP client internals to its callers.
 */
class InsightsAiException(
    message: String,
) : RuntimeException(message)
