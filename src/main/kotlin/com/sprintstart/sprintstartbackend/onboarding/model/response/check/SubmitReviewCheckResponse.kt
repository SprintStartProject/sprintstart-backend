package com.sprintstart.sprintstartbackend.onboarding.model.response.check

/**
 * Grading result of a review-pool submission.
 *
 * Unlike a phase check there is no pass threshold: every correctly answered question
 * leaves the pool for good, while a wrong answer simply keeps it open for another try.
 * [onboardingCompleted] reports whether clearing the pool finished the whole onboarding
 * journey, so the UI can celebrate and refresh the user's profile.
 */
data class SubmitReviewCheckResponse(
    val answeredCount: Int,
    val correctCount: Int,
    val remainingCount: Int,
    val onboardingCompleted: Boolean,
    val results: List<CheckAnswerResultResponse>,
)
