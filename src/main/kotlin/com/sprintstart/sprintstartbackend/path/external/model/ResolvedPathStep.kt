package com.sprintstart.sprintstartbackend.path.external.model

import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTasksResponse
import java.util.UUID

/**
 * One step of a path, by its [id], with the title of the phase it belongs to.
 *
 * Tasks and resources come in the shapes the path page serves, so whoever shows a step can never describe
 * a task or resource differently than the path does.
 */
data class ResolvedPathStep(
    val phaseTitle: String,
    val id: UUID,
    val title: String,
    val description: String,
    val status: StepStatus,
    val aiAssisted: Boolean,
    val expectedOutcome: String,
    val tasks: List<GetOnboardingTasksResponse>,
    val resources: List<GetOnboardingResourcesResponse>,
)
