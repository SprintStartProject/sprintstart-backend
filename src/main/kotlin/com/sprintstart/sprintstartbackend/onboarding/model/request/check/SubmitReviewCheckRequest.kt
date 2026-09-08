package com.sprintstart.sprintstartbackend.onboarding.model.request.check

/**
 * Answers submitted for the standalone review check.
 *
 * A partial submission is allowed: only the answered questions are graded, any open
 * question left out simply stays in the pool.
 */
data class SubmitReviewCheckRequest(
    val answers: List<SubmitCheckAnswerRequest> = emptyList(),
)
