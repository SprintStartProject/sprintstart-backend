package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.path.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.repository.OnboardingTaskRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Keeps a step's tasks in a contiguous order as tasks are added, moved and removed. */
internal class TaskOrdering(
    private val onboardingTaskRepository: OnboardingTaskRepository,
) {
    /**
     * Builds a task for [step] at the requested position, shifting the tasks at or after it one to the
     * right, and reopens the step if it was finished.
     *
     * Valid positions are from `0` to `taskCount`, where `taskCount` means appending at the end.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the position is outside the valid range.
     */
    fun insert(step: OnboardingStep, request: CreateOnboardingTaskRequest): OnboardingTask {
        val taskCount = onboardingTaskRepository.countByStepId(step.id)

        if (request.position !in 0..taskCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and $taskCount")
        }

        onboardingTaskRepository
            .findByStepIdAndPositionGreaterThanEqualOrderByPositionDesc(step.id, request.position)
            .forEach { it.position += 1 }

        val task = OnboardingTask(
            step = step,
            position = request.position,
            title = request.title,
            description = request.description,
        )
        reopenStepIfFinished(step)
        return task
    }

    /**
     * Applies [request] to [task], moving it within its step: when it moves down the tasks between the
     * old and new position shift one to the left, when it moves up they shift one to the right.
     *
     * Valid positions are from `0` to `taskCount - 1`, because the task can only move within the current
     * list.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the position is outside the valid range.
     */
    fun update(task: OnboardingTask, request: UpdateOnboardingTaskRequest) {
        moveTo(task, request.position)

        task.position = request.position
        task.title = request.title
        task.description = request.description
        task.finished = request.finished
    }

    /** Moves the tasks behind [task] up one position, closing the gap it leaves when it is deleted. */
    fun closeGapBehind(task: OnboardingTask) {
        onboardingTaskRepository
            .findAllByStepIdAndPositionGreaterThan(task.step.id, task.position)
            .forEach { it.position -= 1 }
    }

    private fun moveTo(task: OnboardingTask, newPosition: Int) {
        val taskCount = onboardingTaskRepository.countByStepId(task.step.id)

        if (newPosition !in 0 until taskCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${taskCount - 1}")
        }

        val oldPosition = task.position

        if (oldPosition < newPosition) {
            onboardingTaskRepository
                .findByStepIdAndPositionBetween(task.step.id, oldPosition + 1, newPosition)
                .forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            onboardingTaskRepository
                .findByStepIdAndPositionBetween(task.step.id, newPosition, oldPosition - 1)
                .forEach { it.position += 1 }
        }
    }

    /**
     * Reopens a completed step when new work is added to it.
     *
     * A step may only remain [StepStatus.FINISHED] while all of its tasks are done, so adding an
     * unfinished task reverts it to [StepStatus.IN_PROGRESS] and clears its completion timestamp.
     * There is no separate PM endpoint to reset another user's step status.
     */
    private fun reopenStepIfFinished(step: OnboardingStep) {
        if (step.status == StepStatus.FINISHED) {
            step.status = StepStatus.IN_PROGRESS
            step.completedAt = null
        }
    }
}
