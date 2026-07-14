package com.sprintstart.sprintstartbackend.artifacts.model.exceptions

/**
 * Thrown when the AI service fails to deliver an artifact summary.
 *
 * Wraps transport-level failures from the outbound AI call so the artifacts module does not leak
 * HTTP client internals to its callers.
 */
class ArtifactSummaryAiException(
    message: String,
) : RuntimeException(message)
