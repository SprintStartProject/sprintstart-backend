package com.sprintstart.sprintstartbackend.onboarding.model.response.check

/**
 * The user's open review pool: questions from earlier phases that were answered
 * incorrectly and still have to be answered correctly once.
 *
 * Reuses [CheckQuestionForUserResponse] so the questions render exactly like a phase
 * check; every entry carries `review = true` and the title of the phase it came from.
 * Correct answers are never exposed here, only in the submit result.
 */
data class GetReviewCheckResponse(
    val openCount: Int,
    val questions: List<CheckQuestionForUserResponse>,
)
