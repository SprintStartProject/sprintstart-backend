package com.sprintstart.sprintstartbackend.onboarding.model.response.assessment

import java.util.UUID

/**
 * @param done Set when the project has nothing configured to assess yet (no live competency
 * module), so the session finishes immediately with no question and no placement written -- an
 * honest empty result, not a failure. [question] is null exactly when this is true.
 */
data class StartAssessmentResponse(
    val sessionId: UUID,
    val question: String? = null,
    val done: Boolean = false,
)
