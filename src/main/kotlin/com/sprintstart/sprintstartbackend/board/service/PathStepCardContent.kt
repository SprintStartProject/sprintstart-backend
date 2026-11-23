package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.model.response.board.PathStepContent
import com.sprintstart.sprintstartbackend.path.external.model.ResolvedPathStep
import com.sprintstart.sprintstartbackend.path.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTasksResponse
import java.util.UUID

// What a `PATH_STEP` card shows: a pure mapping from the hire's path. [BoardService] decides which cards are
// on the board.

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
 * [GetOnboardingResourcesResponse] — so a `PATH_STEP` card can never describe a task or resource
 * differently than the path does.
 */
internal fun ResolvedPathStep.toContent(): PathStepContent = PathStepContent(
    stepId = id,
    phaseTitle = phaseTitle,
    title = title,
    description = description,
    status = status,
    isAiAssisted = aiAssisted,
    expectedOutcomes = listOf(expectedOutcome),
    tasks = tasks,
    resources = resources,
    reason = null,
)
