package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintTask
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.CreateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.CreateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.GetBlueprintTaskResponse
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
    private val blueprintTaskRepository: BlueprintTaskRepository,
    private val blueprintStepRepository: BlueprintStepRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintTasksForStep(stepId: UUID): List<GetBlueprintTaskResponse> {
        return blueprintTaskRepository
            .findAllByBlueprintStepId(stepId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintTaskById(taskId: UUID): GetBlueprintTaskResponse {
        return blueprintTaskRepository
            .findById(taskId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Blueprint task not Found")
            }.toGetResponse()
    }

    @Transactional
    fun createBlueprintTaskForStep(
        stepId: UUID,
        request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        val blueprintStep = blueprintStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found with id: $stepId") }

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
    fun updateBlueprintTaskById(taskId: UUID, request: UpdateBlueprintTaskRequest): UpdateBlueprintTaskResponse {
        val blueprintTask = blueprintTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found with id: $taskId") }

        shiftTasksBetween(blueprintTask, request)

        blueprintTask.position = request.position
        blueprintTask.title = request.title
        blueprintTask.description = request.description

        return blueprintTaskRepository.save(blueprintTask).toUpdateResponse()
    }

    @Transactional
    fun deleteBlueprintTaskById(taskId: UUID) {
        val blueprintTask = blueprintTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found with id: $taskId") }

        blueprintTaskRepository.delete(blueprintTask)
    }

    // Helper Methods

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
        request: UpdateBlueprintTaskRequest,
    ) {
        val taskCount = blueprintTaskRepository.countByBlueprintStepId(task.blueprintStep.id)

        if (request.position !in 0 until taskCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${taskCount - 1}")
        }

        val oldPosition = task.position
        val newPosition = request.position
        var tasksToShift: MutableList<BlueprintTask>

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
    }
}
