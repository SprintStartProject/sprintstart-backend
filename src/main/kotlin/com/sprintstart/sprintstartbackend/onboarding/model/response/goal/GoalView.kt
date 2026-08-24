package com.sprintstart.sprintstartbackend.onboarding.model.response.goal

import java.util.UUID

/**
 * The starter-work task a hire has committed to on a project.
 *
 * The goal points at the task itself, so [title] and [summary] come from the proposal: the
 * wording of the work, not of a synthetic skill standing in for it.
 */
data class GoalView(
    val proposalId: UUID,
    val title: String,
    val summary: String?,
    val sourceUrl: String?,
)
