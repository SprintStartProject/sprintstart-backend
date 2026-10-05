package com.sprintstart.sprintstartbackend.user.model.exceptions

/**
 * Thrown when the AI service fails to return skill suggestions.
 *
 * Preserves the upstream [statusCode] and raw response [body] so callers and exception
 * handlers can distinguish transient outages from client errors.
 */
class SkillSuggestionAiException(
    val statusCode: Int,
    val body: String,
    message: String,
) : RuntimeException(message)
