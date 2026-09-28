package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.CreateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.DeleteBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.CreateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.GetBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintTaskRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.ranges.contains

/**
 * Manages ordered tasks attached to blueprint steps.
 *
 * Reads are restricted through scope-specific repository queries. Writes require a draft blueprint and matching
 * revision, while insertion and movement shift affected siblings to preserve a contiguous task order.
 */
@Service
class BlueprintTaskService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintTaskRepository: BlueprintTaskRepository,
) {
    /**
     * Returns tasks for step.
     *
     * Runs the repository query matching the requested scope and maps the ordered task entities to response DTOs.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintTasksForStep(
        scope: BlueprintScope,
        stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return when (scope) {
            is BlueprintScope.Global -> {
                blueprintTaskRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintStepId(stepId)
            }

            is BlueprintScope.Project -> {
                blueprintTaskRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(
                        scope.projectId,
                        stepId,
                    )
            }
        }.map { it.toGetResponse() }
    }

    /**
     * Returns task by id.
     *
     * Uses the access service to enforce the ownership scope before mapping the task.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param taskId Identifier of the blueprint task.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
    @Transactional(readOnly = true)
    fun getBlueprintTaskById(
        scope: BlueprintScope,
        taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintAccessService
            .getAuthorizedTask(scope, taskId)
            .toGetResponse()
    }

    /**
     * Creates task for step.
     *
     * Requires an editable parent, validates the insertion position, shifts later siblings right, and persists the
     * new task.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid insertion position, 404 for a missing parent, or 409 when
     *   its path is not a draft.
     */
    @Transactional
    fun createBlueprintTaskForStep(
        scope: BlueprintScope,
        stepId: UUID,
        request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        val blueprintStep = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)

        shiftTasksRight(blueprintStep, request)

        val blueprintTask = BlueprintTask(
            blueprintStep = blueprintStep,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return blueprintTaskRepository.save(blueprintTask).toCreateResponse()
    }

    /**
     * Updates task by id.
     *
     * Requires an editable task and matching revision, shifts siblings when its position changes, and persists the
     * replacement values.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param taskId Identifier of the blueprint task.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintTaskById(
        scope: BlueprintScope,
        taskId: UUID,
        request: UpdateBlueprintTaskRequest,
    ): UpdateBlueprintTaskResponse {
        val blueprintTask = blueprintAccessService.getAuthorizedEditableTask(scope, taskId)

        validateRevision(blueprintTask, request.revision)

        shiftTasksBetween(blueprintTask, request.position)

        blueprintTask.position = request.position
        blueprintTask.title = request.title
        blueprintTask.description = request.description

        return blueprintTaskRepository.save(blueprintTask).toUpdateResponse()
    }

    /**
     * Updates task position by id.
     *
     * Requires an editable task and matching revision, shifts intervening siblings, and flushes every changed
     * position together.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param taskId Identifier of the blueprint task.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintTaskPositionById(
        scope: BlueprintScope,
        taskId: UUID,
        request: UpdateBlueprintTaskPositionRequest,
    ): List<UpdateBlueprintTaskPositionResponse> {
        val blueprintTask = blueprintAccessService.getAuthorizedEditableTask(scope, taskId)

        validateRevision(blueprintTask, request.revision)

        val shiftedTasks = shiftTasksBetween(blueprintTask, request.position)
        blueprintTask.position = request.position
        shiftedTasks.add(blueprintTask)
        blueprintTaskRepository.saveAllAndFlush(shiftedTasks)

        return shiftedTasks.map { it.toUpdatePositionResponse() }
    }

    /**
     * Deletes task by id.
     *
     * Requires an editable task and matching revision before deletion.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param taskId Identifier of the blueprint task.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @throws ResponseStatusException With 404 for a missing entity, or 409 for a stale revision or non-draft path.
     */
    @Transactional
    fun deleteBlueprintTaskById(
        scope: BlueprintScope,
        taskId: UUID,
        request: DeleteBlueprintTaskRequest,
    ) {
        val blueprintTask = blueprintAccessService.getAuthorizedEditableTask(scope, taskId)

        validateRevision(blueprintTask, request.revision)

        blueprintTaskRepository.delete(blueprintTask)
    }

    // Helper Methods

    private fun validateRevision(
        task: BlueprintTask,
        revision: Long,
    ) {
        if (task.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint task has been modified by another request. Please reload and try again.",
            )
        }
    }

    private fun shiftTasksRight(
        step: BlueprintStep,
        request: CreateBlueprintTaskRequest,
    ) {
        val taskCount = blueprintTaskRepository.countByBlueprintStepId(step.id)

        if (request.position !in 0..taskCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $taskCount",
            )
        }

        val tasksToShift = blueprintTaskRepository
            .findByBlueprintStepIdAndPositionGreaterThanEqualOrderByPositionDesc(
                step.id,
                request.position,
            )

        tasksToShift.forEach { it.position += 1 }
    }

    private fun shiftTasksBetween(
        task: BlueprintTask,
        newPosition: Int,
    ): MutableList<BlueprintTask> {
        val taskCount = blueprintTaskRepository.countByBlueprintStepId(task.blueprintStep.id)

        if (newPosition !in 0 until taskCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${taskCount - 1}")
        }

        val oldPosition = task.position
        var tasksToShift: MutableList<BlueprintTask> = mutableListOf()

        if (oldPosition < newPosition) {
            tasksToShift = blueprintTaskRepository
                .findByBlueprintStepIdAndPositionBetween(
                    task.blueprintStep.id,
                    oldPosition + 1,
                    newPosition,
                )

            tasksToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            tasksToShift = blueprintTaskRepository
                .findByBlueprintStepIdAndPositionBetween(
                    task.blueprintStep.id,
                    newPosition,
                    oldPosition - 1,
                )

            tasksToShift.forEach { it.position += 1 }
        }

        return tasksToShift
    }
}
