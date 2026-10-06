package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.PathStepContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import java.util.UUID

// What a `PATH_STEP` card shows. Pure mapping from the path the hire already has, kept beside
// [BoardService] rather than inside it: the service decides which card is on the board, and this
// only says what one of them reads as.

/**
 * A step of the hire's path, read live — or, when [subject] no longer names one, why not.
 *
 * The path can be regenerated out from under a card that still points at a step which has since
 * gone, and this is the honest state for that: the card degrades rather than vanishing, the same
 * way [DiagramContent.reason] does for a picture that can no longer be drawn.
 */
internal fun pathStepContent(
    subject: String?,
    pathSteps: Map<UUID, ResolvedPathStep>,
): PathStepContent {
    val stepId = subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    return stepId?.let { pathSteps[it] }?.toContent() ?: PathStepContent(
        stepId = null,
        phaseTitle = null,
        title = null,
        description = null,
        status = null,
        isAiAssisted = false,
        expectedOutcomes = emptyList(),
        tasks = emptyList(),
        resources = emptyList(),
        reason = "This step is no longer on the hire's path.",
    )
}

/**
 * The same shapes the path page itself serves — [GetOnboardingTasksResponse] and
 * [GetOnboardingResourcesResponse] via their own mappers — so a `PATH_STEP` card can never
 * describe a task or resource differently than the path does.
 */
internal fun ResolvedPathStep.toContent(): PathStepContent = PathStepContent(
    stepId = step.id,
    phaseTitle = phase.title,
    title = step.title,
    description = step.description,
    status = step.status,
    isAiAssisted = step.aiAssisted,
    expectedOutcomes = listOf(step.expectedOutcome),
    tasks = step.tasks.sortedBy { it.position }.map { it.toGetAllResponse() },
    resources = step.resources.map { it.toGetAllResponse() },
    reason = null,
)
