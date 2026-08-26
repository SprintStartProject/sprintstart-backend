package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
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
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintTaskRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.ranges.contains

@Service
class BlueprintTaskService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintTaskRepository: BlueprintTaskRepository,
    private val blueprintStepRepository: BlueprintStepRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintTasksForStep(
        projectId: UUID,
        stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return blueprintTaskRepository
            .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(projectId, stepId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintTaskById(
        projectId: UUID,
        taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintAccessService
            .getAuthorizedTask(projectId, taskId)
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintTaskForStep(
        projectId: UUID,
        stepId: UUID,
        request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        val blueprintStep = blueprintAccessService.getAuthorizedEditableStep(projectId, stepId)

        shiftTasksRight(blueprintStep, request)

        val blueprintTask = BlueprintTask(
            blueprintStep = blueprintStep,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return blueprintTaskRepository.save(blueprintTask).toCreateResponse()
    }

    @Transactional
    fun updateBlueprintTaskById(
        projectId: UUID,
        taskId: UUID,
        request: UpdateBlueprintTaskRequest,
    ): UpdateBlueprintTaskResponse {
        val blueprintTask = blueprintAccessService.getAuthorizedEditableTask(projectId, taskId)

        validateRevision(blueprintTask, request.revision)

        shiftTasksBetween(blueprintTask, request.position)

        blueprintTask.position = request.position
        blueprintTask.title = request.title
        blueprintTask.description = request.description

        return blueprintTaskRepository.save(blueprintTask).toUpdateResponse()
    }

    @Transactional
    fun updateBlueprintTaskPositionById(
        projectId: UUID,
        taskId: UUID,
        request: UpdateBlueprintTaskPositionRequest,
    ): List<UpdateBlueprintTaskPositionResponse> {
        val blueprintTask = blueprintAccessService.getAuthorizedEditableTask(projectId, taskId)

        validateRevision(blueprintTask, request.revision)

        val shiftedTasks = shiftTasksBetween(blueprintTask, request.position)
        blueprintTask.position = request.position
        shiftedTasks.add(blueprintTask)
        blueprintTaskRepository.saveAllAndFlush(shiftedTasks)

        return shiftedTasks.map { it.toUpdatePositionResponse() }
    }

    @Transactional
    fun deleteBlueprintTaskById(
        projectId: UUID,
        taskId: UUID,
        request: DeleteBlueprintTaskRequest,
    ) {
        val blueprintTask = blueprintAccessService.getAuthorizedEditableTask(projectId, taskId)

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
