package com.sprintstart.sprintstartbackend.insights.model.exceptions

/**
 * Thrown when the AI service fails to deliver knowledge-gap classifications.
 *
 * Wraps transport-level failures from the outbound AI call so the insights module does not leak
 * HTTP client internals to its callers.
 */
class KnowledgeGapsAiException(
    message: String,
) : RuntimeException(message)
