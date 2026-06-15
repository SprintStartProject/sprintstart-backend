package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTaskRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach
import kotlin.ranges.contains

/**
 * Manages onboarding tasks within a step.
 *
 * Tasks are ordered siblings under a step. Create, update, and delete operations
 * maintain contiguous positions by shifting neighboring tasks when necessary.
 */
@Suppress("TooManyFunctions")
@Service
class OnboardingTaskService(
    private val onboardingTaskRepository: OnboardingTaskRepository,
    private val onboardingStepRepository: OnboardingStepRepository,
    private val userApi: UserApi,
) {
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

        shiftTasksRight(step, request)

        val onboardingTask = OnboardingTask(
            step = step,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return onboardingTaskRepository.save(onboardingTask).toCreateResponse()
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

        shiftTasksBetween(task, request)

        task.position = request.position
        task.title = request.title
        task.description = request.description
        task.finished = request.finished

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
    fun createOnboardingTaskForStepId(
        stepId: UUID,
        request: CreateOnboardingTaskRequest,
    ): CreateOnboardingTaskResponse {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        shiftTasksRight(step, request)

        val onboardingTask = OnboardingTask(
            step = step,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return onboardingTaskRepository.save(onboardingTask).toCreateResponse()
    }

    /**
     * Retrieves a single onboarding task by its ID.
     *
     * @param taskId The ID of the onboarding task.
     * @return The onboarding task response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no task exists with [taskId].
     */
    @Transactional(readOnly = true)
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
    fun updateOnboardingTaskById(taskId: UUID, request: UpdateOnboardingTaskRequest): UpdateOnboardingTaskResponse {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        shiftTasksBetween(task, request)

        task.position = request.position
        task.title = request.title
        task.description = request.description
        task.finished = request.finished

        return onboardingTaskRepository.save(task).toUpdateResponse()
    }

    /**
     * Deletes an onboarding task and shifts subsequent sibling tasks one position back.
     *
     * @param taskId The ID of the task to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no task exists with [taskId].
     */
    @Transactional
    fun deleteOnboardingTaskById(taskId: UUID) {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        val tasksToShift = onboardingTaskRepository
            .findAllByStepIdAndPositionGreaterThan(task.step.id, task.position)
        tasksToShift.forEach { it.position -= 1 }

        onboardingTaskRepository.delete(task)
    }

//  ========================== Helper Methods ==========================

    /**
     * Makes room for a new task at the requested position by shifting all existing
     * tasks at that position or after it one position to the right.
     *
     * Valid positions are from `0` to `taskCount`, where `taskCount` means
     * appending the new task at the end.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the requested
     * position is outside the valid range.
     */
    private fun shiftTasksRight(
        step: OnboardingStep,
        request: CreateOnboardingTaskRequest,
    ) {
        val taskCount = onboardingTaskRepository.countByStepId(step.id)

        if (request.position !in 0..taskCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $taskCount",
            )
        }

        val tasksToShift = onboardingTaskRepository
            .findByStepIdAndPositionGreaterThanEqualOrderByPositionDesc(
                step.id,
                request.position,
            )

        tasksToShift.forEach { it.position += 1 }
    }

    /**
     * Reorders tasks after an existing task is moved to a new position.
     *
     * When the task moves down, all tasks between the old and new position are
     * shifted one position to the left. When the task moves up, all tasks between
     * the new and old position are shifted one position to the right.
     *
     * Valid positions are from `0` to `taskCount - 1`, because the task already
     * exists and can only be moved within the current list.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the requested
     * position is outside the valid range.
     */
    private fun shiftTasksBetween(
        task: OnboardingTask,
        request: UpdateOnboardingTaskRequest,
    ) {
        val taskCount = onboardingTaskRepository.countByStepId(task.step.id)

        if (request.position !in 0 until taskCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${taskCount - 1}")
        }

        val oldPosition = task.position
        val newPosition = request.position

        if (oldPosition < newPosition) {
            val stepsToShift = onboardingTaskRepository
                .findByStepIdAndPositionBetween(
                    task.step.id,
                    oldPosition + 1,
                    newPosition,
                )

            stepsToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            val stepsToShift = onboardingTaskRepository
                .findByStepIdAndPositionBetween(
                    task.step.id,
                    newPosition,
                    oldPosition - 1,
                )

            stepsToShift.forEach { it.position += 1 }
        }
    }
}
