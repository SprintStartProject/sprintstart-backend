package com.sprintstart.sprintstartbackend.onboarding.model.request.question

import java.util.UUID

/**
 * Answer submitted for a single knowledge-check question.
 *
 * Exactly one of [selectedOptionIds] (MULTIPLE_CHOICE) or [textAnswer] (SHORT_TEXT) is
 * expected, matching the question's type.
 */
data class SubmitQuestionAttemptRequest(
    val selectedOptionIds: List<UUID> = emptyList(),
    val textAnswer: String? = null,
)
