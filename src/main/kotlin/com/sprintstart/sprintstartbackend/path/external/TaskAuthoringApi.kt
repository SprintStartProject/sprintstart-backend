package com.sprintstart.sprintstartbackend.path.external

import com.sprintstart.sprintstartbackend.path.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.StepId

/**
 * The tasks (checklist lines) of a team member's steps, as their manager edits them.
 *
 * By id and without an access check, as [PathReviewApi]. A call that names a task or step that is not
 * there throws a 404 [org.springframework.web.server.ResponseStatusException].
 */
interface TaskAuthoringApi {
    /** Adds a task to step [stepId]. */
    fun createTask(stepId: StepId, request: CreateOnboardingTaskRequest): CreateOnboardingTaskResponse

    /** Task [taskId]. */
    fun task(taskId: OnboardingTaskId): GetOnboardingTaskResponse

    /** Every task of step [stepId]. */
    fun tasksOf(stepId: StepId): List<GetOnboardingTaskResponse>

    /** Replaces task [taskId]. */
    fun updateTask(taskId: OnboardingTaskId, request: UpdateOnboardingTaskRequest): UpdateOnboardingTaskResponse

    /** Deletes task [taskId]. */
    fun deleteTask(taskId: OnboardingTaskId)
}
