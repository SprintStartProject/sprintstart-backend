package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.path.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.path.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.path.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.path.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.path.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTasksResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.path.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.path.repository.OnboardingTaskRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Manages onboarding tasks within a step.
 *
 * Tasks are ordered siblings under a step. Create, update, and delete operations
 * maintain contiguous positions by shifting neighboring tasks when necessary.
 */
@Service
class OnboardingTaskService(
    private val onboardingTaskRepository: OnboardingTaskRepository,
    private val onboardingStepRepository: OnboardingStepRepository,
    private val userApi: UserApi,
) {
    private val taskOrdering = TaskOrdering(onboardingTaskRepository)

//  ========================== Methods for users ==========================

    /**
     * Returns all tasks for one step in the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param stepId Identifier of the parent step.
     * @return Ordered tasks for the step.
     * @throws ResponseStatusException When the user does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving an onboarding task")
    fun getOnboardingTasksForMe(authId: String, stepId: UUID): List<GetOnboardingTasksResponse> {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return onboardingTaskRepository
            .findAllByStepIdAndStepPhasePathUserId(stepId, userId)
            .map { it.toGetAllResponse() }
    }

    /**
     * Creates a task in one step of the authenticated user's onboarding path.
     *
     * Existing tasks at or after the requested position are shifted right to make room.
     *
     * @param authId External authentication identifier.
     * @param stepId Identifier of the parent step.
     * @param request Task creation payload.
     * @return The created task.
     * @throws ResponseStatusException When the user, step, or requested position is invalid.
     */
    @Transactional
    @Tracked("Creating a task in an onboarding step")
    fun createOnboardingTaskForMe(
        authId: String,
        stepId: UUID,
        request: CreateOnboardingTaskRequest,
    ): CreateOnboardingTaskResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val step = onboardingStepRepository
            .findByIdAndPhasePathUserId(stepId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found") }

        return onboardingTaskRepository.save(taskOrdering.insert(step, request)).toCreateResponse()
    }

    /**
     * Returns one task from the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param taskId Identifier of the task to load.
     * @return The requested task.
     * @throws ResponseStatusException When the user or task does not exist.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving specific onboarding task for user")
    fun getOnboardingTaskForMe(authId: String, taskId: UUID): GetOnboardingTaskResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return onboardingTaskRepository
            .findByIdAndStepPhasePathUserId(taskId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found") }
            .toGetResponse()
    }

    /**
     * Updates a task in the authenticated user's onboarding path.
     *
     * If the position changes, sibling tasks between the old and new position are shifted
     * to preserve contiguous ordering.
     *
     * @param authId External authentication identifier.
     * @param taskId Identifier of the task to update.
     * @param request Task update payload.
     * @return The updated task.
     * @throws ResponseStatusException When the user, task, or requested position is invalid.
     */
    @Transactional
    @Tracked("Updating an onboarding task for user")
    fun updateOnboardingTaskForMe(
        authId: String,
        taskId: UUID,
        request: UpdateOnboardingTaskRequest,
    ): UpdateOnboardingTaskResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val task = onboardingTaskRepository
            .findByIdAndStepPhasePathUserId(taskId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found") }

        taskOrdering.update(task, request)

        return task.toUpdateResponse()
    }

    /**
     * Deletes a task from the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param taskId Identifier of the task to delete.
     * @throws ResponseStatusException When the user or task does not exist.
     */
    @Transactional
    @Tracked("Deleting an onboarding task for user")
    fun deleteOnboardingTaskForMe(authId: String, taskId: UUID) {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val task = onboardingTaskRepository
            .findByIdAndStepPhasePathUserId(taskId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found") }

        onboardingTaskRepository.delete(task)
    }

//  ========================== Methods for admins ==========================

    /**
     * Retrieves all onboarding tasks belonging to the specified step.
     *
     * @param stepId The ID of the step whose tasks should be retrieved.
     * @return A list of tasks for the given step.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding tasks for admin by step id")
    fun getOnboardingTasksByStepId(stepId: UUID): List<GetOnboardingTaskResponse> {
        return onboardingTaskRepository
            .findAllByStepId(stepId)
            .map { it.toGetResponse() }
    }

    /**
     * Creates a new onboarding task within the specified step at the requested position.
     *
     * All existing tasks at or after [CreateOnboardingTaskRequest.position] are shifted
     * one position forward.
     *
     * @param stepId The ID of the step to add the task to.
     * @param request The task creation request.
     * @return The created task response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no step exists with [stepId].
     */
    @Transactional
    @Tracked("Creating new onboarding task for step for admin")
    fun createOnboardingTaskForStepId(
        stepId: UUID,
        request: CreateOnboardingTaskRequest,
    ): CreateOnboardingTaskResponse {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        return onboardingTaskRepository.save(taskOrdering.insert(step, request)).toCreateResponse()
    }

    /**
     * Retrieves a single onboarding task by its ID.
     *
     * @param taskId The ID of the onboarding task.
     * @return The onboarding task response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no task exists with [taskId].
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving onboarding task for admin")
    fun getOnboardingTaskById(taskId: UUID): GetOnboardingTaskResponse {
        return onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }
            .toGetResponse()
    }

    /**
     * Updates an existing onboarding task, including repositioning it within its step.
     *
     * When the position changes, sibling tasks between the old and new positions are shifted
     * to maintain contiguous ordering.
     *
     * @param taskId The ID of the task to update.
     * @param request The update request.
     * @return The updated task response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no task exists with [taskId].
     */
    @Transactional
    @Tracked("Updating onboarding task for admin")
    fun updateOnboardingTaskById(taskId: UUID, request: UpdateOnboardingTaskRequest): UpdateOnboardingTaskResponse {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        taskOrdering.update(task, request)

        return onboardingTaskRepository.save(task).toUpdateResponse()
    }

    /**
     * Deletes an onboarding task and shifts subsequent sibling tasks one position back.
     *
     * @param taskId The ID of the task to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no task exists with [taskId].
     */
    @Transactional
    @Tracked("Deleting onboarding task for admin")
    fun deleteOnboardingTaskById(taskId: UUID) {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        taskOrdering.closeGapBehind(task)

        onboardingTaskRepository.delete(task)
    }

    /**
     * Flips one task's finished flag for the authenticated user, and nothing else about it.
     *
     * The single write path for [OnboardingTask.finished]: a caller that needs to change what a task
     * says — its position, title, description — still goes through [updateOnboardingTaskForMe].
     * This one exists so the board's write-back has something narrower to call than a full replace,
     * which needs a position and 400s on a stale one. It leaves the owning step's status untouched,
     * the same as [updateOnboardingTaskForMe] already does: ticking a task must never move the step
     * status underneath it.
     *
     * @param userId The task's owner.
     * @param taskId Identifier of the task to flip.
     * @param finished The value to set.
     * @throws ResponseStatusException When no such task exists on that user's path.
     */
    @Transactional
    @Tracked("Ticking an onboarding task for user")
    fun setFinishedForUser(userId: UUID, taskId: UUID, finished: Boolean) {
        val task = onboardingTaskRepository
            .findByIdAndStepPhasePathUserId(taskId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found") }

        task.finished = finished
    }
}
