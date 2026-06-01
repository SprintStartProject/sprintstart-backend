package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.CreateOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTaskRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Service handling all business logic for the onboarding module.
 *
 * Manages the full hierarchy of onboarding entities:
 * - **Path** — top-level container tied to a specific user
 * - **Phase** — ordered group of steps within a path
 * - **Step** — individual unit of work within a phase, with a [StepStatus]
 * - **Task** — ordered checklist item within a step
 * - **Resource** — reference material (link) attached to a step
 *
 * Position management (insert/update/delete) is handled automatically for
 * phases, steps, and tasks by shifting sibling positions as needed.
 */
@Suppress("TooManyFunctions")
@Service
class OnboardingService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val onboardingStepRepository: OnboardingStepRepository,
    private val onboardingTaskRepository: OnboardingTaskRepository,
    private val onboardingResourceRepository: OnboardingResourceRepository,
    private val userApi: UserApi,
) {
    // -------------------------------------------------------------------------
    // Paths
    // -------------------------------------------------------------------------

    /**
     * Creates a new onboarding path for the given user.
     *
     * @param userId The ID of the user to create the path for.
     * @return The created path response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no user exists with [userId].
     */
    fun createOnboardingPathForUser(userId: UUID): CreateOnboardingPathResponse {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        return onboardingPathRepository.save(OnboardingPath(userId = userId)).toCreateResponse()
    }

    /**
     * Retrieves all onboarding paths across all users.
     *
     * @return A list of all onboarding paths.
     */
    fun getAllOnboardingPaths(): List<GetOnboardingPathsResponse> {
        return onboardingPathRepository.findAll().map {
            it.toGetAllResponse()
        }
    }

    /**
     * Retrieves a single onboarding path by its ID.
     *
     * @param pathId The ID of the onboarding path.
     * @return The onboarding path response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no path exists with [pathId].
     */
    fun getOnboardingPath(pathId: UUID): GetOnboardingPathResponse {
        return onboardingPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No onboarding path found with id: $pathId") }
            .toGetResponse()
    }

    /**
     * Retrieves the onboarding path associated with a specific user.
     *
     * @param userId The ID of the user.
     * @return The user's onboarding path response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no user exists with [userId],
     *   or if no path has been created for that user yet.
     */
    fun getOnboardingPathByUserId(userId: UUID): GetOnboardingPathForUserResponse {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        return onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found for user with id: $userId") }
            .toGetForUserResponse()
    }

    /**
     * Deletes an onboarding path by its ID.
     *
     * No-op if no path exists with the given ID.
     *
     * @param pathId The ID of the onboarding path to delete.
     */
    fun deleteOnboardingPathById(pathId: UUID) {
        onboardingPathRepository.deleteById(pathId)
    }

    /**
     * Deletes the onboarding path associated with a specific user.
     *
     * @param userId The ID of the user whose path should be deleted.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no user exists with [userId].
     */
    fun deleteOnboardingPathByUserId(userId: UUID) {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        onboardingPathRepository.deleteByUserId(userId)
    }

    // -------------------------------------------------------------------------
    // Phases
    // -------------------------------------------------------------------------

    /**
     * Creates a new onboarding phase within the specified path at the requested position.
     *
     * All existing phases at or after [CreateOnboardingPhaseRequest.position] are shifted
     * one position forward to make room for the new phase.
     *
     * @param pathId The ID of the path to add the phase to.
     * @param request The phase creation request containing position, title, and description.
     * @return The created phase response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no path exists with [pathId].
     */
    @Transactional
    fun createOnboardingPhaseForPathId(
        pathId: UUID,
        request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        val path = onboardingPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found with id: $pathId") }

        // Shift all phases at or after the new position up by one
        val phasesToShift = onboardingPhaseRepository.findByPath_IdAndPositionGreaterThanEqual(pathId, request.position)
        phasesToShift.forEach { it.position += 1 }
        onboardingPhaseRepository.saveAll(phasesToShift)

        val onboardingPhase = OnboardingPhase(
            path = path,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return onboardingPhaseRepository.save(onboardingPhase).toCreateResponse()
    }

    /**
     * Retrieves all onboarding phases across all paths.
     *
     * @return A list of all onboarding phases.
     */
    @Transactional(readOnly = true)
    fun getOnboardingPhases(): List<GetOnboardingPhasesResponse> {
        return onboardingPhaseRepository.findAll().map { it.toGetAllResponse() }
    }

    /**
     * Retrieves all onboarding phases belonging to the specified path.
     *
     * @param pathId The ID of the path whose phases should be retrieved.
     * @return A list of phases for the given path.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no path exists with [pathId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingPhasesByPathId(pathId: UUID): List<GetOnboardingPhaseResponse> {
        if (!onboardingPathRepository.existsById(pathId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No path found with id: $pathId")
        }
        return onboardingPhaseRepository.findAllByPath_Id(pathId).map { it.toGetResponse() }
    }

    /**
     * Retrieves a single onboarding phase by its ID.
     *
     * @param phaseId The ID of the onboarding phase.
     * @return The onboarding phase response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no phase exists with [phaseId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingPhase(phaseId: UUID): GetOnboardingPhaseResponse {
        return onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }
            .toGetResponse()
    }

    /**
     * Updates an existing onboarding phase, including repositioning it within its path.
     *
     * When the position changes, sibling phases between the old and new positions are
     * shifted accordingly to maintain a contiguous ordering.
     *
     * @param phaseId The ID of the phase to update.
     * @param request The update request containing the new position, title, and description.
     * @return The updated phase response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no phase exists with [phaseId].
     */
    @Transactional
    fun updateOnboardingPhase(phaseId: UUID, request: UpdateOnboardingPhaseRequest): UpdateOnboardingPhaseResponse {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        var phasesToShift: MutableList<OnboardingPhase> = mutableListOf()
        if (phase.position < request.position) {
            // Shift from new position up to current position
            phasesToShift = onboardingPhaseRepository
                .findByPath_IdAndPositionBetween(phase.path.id, phase.position + 1, request.position)
            phasesToShift.forEach { it.position -= 1 }
        }
        if (phase.position > request.position) {
            phasesToShift = onboardingPhaseRepository
                .findByPath_IdAndPositionBetween(phase.path.id, request.position, phase.position - 1)
            phasesToShift.forEach { it.position += 1 }
        }
        onboardingPhaseRepository.saveAll(phasesToShift)

        phase.position = request.position
        phase.title = request.title
        phase.description = request.description

        return onboardingPhaseRepository.save(phase).toUpdateResponse()
    }

    /**
     * Deletes an onboarding phase and shifts subsequent sibling phases one position back.
     *
     * @param phaseId The ID of the phase to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no phase exists with [phaseId].
     */
    @Transactional
    fun deleteOnboardingPhase(phaseId: UUID) {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        // Shift left
        val phasesToShift = onboardingPhaseRepository
            .findByPath_IdAndPositionGreaterThan(phase.path.id, phase.position)
        phasesToShift.forEach { it.position -= 1 }
        if (phasesToShift.isNotEmpty()) onboardingPhaseRepository.saveAll(phasesToShift)

        onboardingPhaseRepository.delete(phase)
    }

    // -------------------------------------------------------------------------
    // Steps
    // -------------------------------------------------------------------------

    /**
     * Creates a new onboarding step within the specified phase at the requested position.
     *
     * All existing steps at or after [CreateOnboardingStepRequest.position] are shifted
     * one position forward. The new step is initialized with [StepStatus.WAITING].
     *
     * @param phaseId The ID of the phase to add the step to.
     * @param request The step creation request.
     * @return The created step response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no phase exists with [phaseId].
     */
    @Transactional
    fun createOnboardingStepForPhaseId(
        phaseId: UUID,
        request: CreateOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        // Shift right
        val stepsToShift = onboardingStepRepository
            .findByPhase_IdAndPositionGreaterThanEqual(phaseId, request.position)
        stepsToShift.forEach { it.position += 1 }
        if (stepsToShift.isNotEmpty()) onboardingStepRepository.saveAll(stepsToShift)

        val onboardingStep = OnboardingStep(
            phase = phase,
            position = request.position,
            title = request.title,
            description = request.description,
            type = request.type,
            estimatedMinutes = request.estimatedMinutes,
            expectedOutcome = request.expectedOutcome,
            status = StepStatus.WAITING,
        )

        return onboardingStepRepository.save(onboardingStep).toCreateResponse()
    }

    /**
     * Retrieves all onboarding steps across all phases.
     *
     * @return A list of all onboarding steps.
     */
    @Transactional(readOnly = true)
    fun getOnboardingSteps(): List<GetOnboardingStepsResponse> {
        return onboardingStepRepository
            .findAll()
            .map { it.toGetAllResponse() }
    }

    /**
     * Retrieves all onboarding steps belonging to the specified phase.
     *
     * @param phaseId The ID of the phase whose steps should be retrieved.
     * @return A list of steps for the given phase.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no phase exists with [phaseId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingStepsByPhaseId(phaseId: UUID): List<GetOnboardingStepResponse> {
        if (!onboardingPhaseRepository.existsById(phaseId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId")
        }
        return onboardingStepRepository
            .findAllByPhase_Id(phaseId)
            .map { it.toGetResponse() }
    }

    /**
     * Retrieves a single onboarding step by its ID.
     *
     * @param stepId The ID of the onboarding step.
     * @return The onboarding step response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no step exists with [stepId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingStep(stepId: UUID): GetOnboardingStepResponse {
        return onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }
            .toGetResponse()
    }

    /**
     * Updates an existing onboarding step, including repositioning and status transitions.
     *
     * When the position changes, sibling steps between the old and new positions are shifted
     * to maintain contiguous ordering.
     *
     * Status transition side-effects:
     * - [StepStatus.FINISHED] — sets [OnboardingStep.completedAt] to now.
     * - [StepStatus.SKIPPED] — sets [OnboardingStep.completedAt] to now and records
     *   [UpdateOnboardingStepRequest.skipReason] (defaults to `"No reason given"`).
     * - [StepStatus.WAITING] — clears [OnboardingStep.completedAt] and [OnboardingStep.skipReason].
     *
     * @param stepId The ID of the step to update.
     * @param request The update request.
     * @return The updated step response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no step exists with [stepId].
     */
    @Transactional
    fun updateOnboardingStep(stepId: UUID, request: UpdateOnboardingStepRequest): UpdateOnboardingStepResponse {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        var stepsToShift: MutableList<OnboardingStep> = mutableListOf()
        if (step.position < request.position) {
            // Shift from new position up to current position
            stepsToShift = onboardingStepRepository
                .findByPhase_IdAndPositionBetween(step.phase.id, step.position + 1, request.position)
            stepsToShift.forEach { it.position -= 1 }
        }
        if (step.position > request.position) {
            stepsToShift = onboardingStepRepository
                .findByPhase_IdAndPositionBetween(step.phase.id, request.position, step.position - 1)
            stepsToShift.forEach { it.position += 1 }
        }
        onboardingStepRepository.saveAll(stepsToShift)

        step.position = request.position
        step.title = request.title
        step.description = request.description
        step.type = request.type
        step.estimatedMinutes = request.estimatedMinutes
        step.expectedOutcome = request.expectedOutcome

        if (step.status != request.status) {
            when (request.status) {
                StepStatus.FINISHED -> {
                    step.completedAt = Instant.now()
                    step.skipReason = null
                }

                StepStatus.SKIPPED -> {
                    step.completedAt = Instant.now()
                    step.skipReason = request.skipReason ?: "No reason given"
                }

                StepStatus.WAITING -> {
                    step.completedAt = null
                    step.skipReason = ""
                }
            }
        }

        step.status = request.status

        return onboardingStepRepository.save(step).toUpdateResponse()
    }

    /**
     * Deletes an onboarding step and shifts subsequent sibling steps one position back.
     *
     * @param stepId The ID of the step to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no step exists with [stepId].
     */
    @Transactional
    fun deleteOnboardingStep(stepId: UUID) {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        // Shift left
        val stepsToShift = onboardingStepRepository
            .findByPhase_IdAndPositionGreaterThan(step.phase.id, step.position)
        stepsToShift.forEach { it.position -= 1 }
        onboardingStepRepository.saveAll(stepsToShift)

        onboardingStepRepository.delete(step)
    }

    // -------------------------------------------------------------------------
    // Tasks
    // -------------------------------------------------------------------------

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

        val tasksToShift = onboardingTaskRepository
            .findByStep_IdAndPositionGreaterThanEqual(stepId, request.position)
        tasksToShift.forEach { it.position += 1 }
        onboardingTaskRepository.saveAll(tasksToShift)

        val onboardingTask = OnboardingTask(
            step = step,
            position = request.position,
            title = request.title,
            description = request.description,
        )

        return onboardingTaskRepository.save(onboardingTask).toCreateResponse()
    }

    /**
     * Retrieves all onboarding tasks across all steps.
     *
     * @return A list of all onboarding tasks.
     */
    @Transactional(readOnly = true)
    fun getOnboardingTasks(): List<GetOnboardingTasksResponse> {
        return onboardingTaskRepository.findAll().map { it.toGetAllResponse() }
    }

    /**
     * Retrieves all onboarding tasks belonging to the specified step.
     *
     * @param stepId The ID of the step whose tasks should be retrieved.
     * @return A list of tasks for the given step.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no step exists with [stepId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingTasksByStepId(stepId: UUID): List<GetOnboardingTaskResponse> {
        if (!onboardingStepRepository.existsById(stepId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId")
        }
        return onboardingTaskRepository.findAllByStep_Id(stepId).map { it.toGetResponse() }
    }

    /**
     * Retrieves a single onboarding task by its ID.
     *
     * @param taskId The ID of the onboarding task.
     * @return The onboarding task response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no task exists with [taskId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingTask(taskId: UUID): GetOnboardingTaskResponse {
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
    fun updateOnboardingTask(taskId: UUID, request: UpdateOnboardingTaskRequest): UpdateOnboardingTaskResponse {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        var tasksToShift: MutableList<OnboardingTask> = mutableListOf()
        if (task.position < request.position) {
            // Shift from new position up to current position
            tasksToShift = onboardingTaskRepository
                .findByStep_IdAndPositionBetween(task.step.id, task.position + 1, request.position)
            tasksToShift.forEach { it.position -= 1 }
        }
        if (task.position > request.position) {
            tasksToShift = onboardingTaskRepository
                .findByStep_IdAndPositionBetween(task.step.id, request.position, task.position - 1)
            tasksToShift.forEach { it.position += 1 }
        }
        onboardingTaskRepository.saveAll(tasksToShift)

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
    fun deleteOnboardingTask(taskId: UUID) {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        val tasksToShift = onboardingTaskRepository
            .findByStep_IdAndPositionGreaterThan(task.step.id, task.position)
        tasksToShift.forEach { it.position -= 1 }
        onboardingTaskRepository.saveAll(tasksToShift)

        onboardingTaskRepository.delete(task)
    }

    // -------------------------------------------------------------------------
    // Resources
    // -------------------------------------------------------------------------

    /**
     * Creates a new onboarding resource (reference link) attached to the specified step.
     *
     * Unlike phases, steps, and tasks, resources are not position-ordered.
     *
     * @param stepId The ID of the step to attach the resource to.
     * @param request The resource creation request containing title, description, and URL.
     * @return The created resource response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no step exists with [stepId].
     */
    @Transactional
    fun createOnboardingResourceForStepId(
        stepId: UUID,
        request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        val resource = OnboardingResource(
            step = step,
            title = request.title,
            description = request.description,
            url = request.url,
        )

        return onboardingResourceRepository.save(resource).toCreateResponse()
    }

    /**
     * Retrieves all onboarding resources across all steps.
     *
     * @return A list of all onboarding resources.
     */
    @Transactional(readOnly = true)
    fun getOnboardingResources(): List<GetOnboardingResourcesResponse> {
        return onboardingResourceRepository
            .findAll()
            .map { it.toGetAllResponse() }
    }

    /**
     * Retrieves all onboarding resources attached to the specified step.
     *
     * @param stepId The ID of the step whose resources should be retrieved.
     * @return A list of resources for the given step.
     */
    @Transactional(readOnly = true)
    fun getOnboardingResourceByStepId(stepId: UUID): List<GetOnboardingResourceResponse> {
        if (!onboardingStepRepository.existsById(stepId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId")
        }
        return onboardingResourceRepository
            .findAllByStep_Id(stepId)
            .map { it.toGetResponse() }
    }

    /**
     * Retrieves a single onboarding resource by its ID.
     *
     * @param resourceId The ID of the onboarding resource.
     * @return The onboarding resource response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no resource exists with [resourceId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingResource(resourceId: UUID): GetOnboardingResourceResponse {
        return onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id $resourceId") }
            .toGetResponse()
    }

    /**
     * Updates an existing onboarding resource's title, description, and URL.
     *
     * @param resourceId The ID of the resource to update.
     * @param request The update request.
     * @return The updated resource response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no resource exists with [resourceId].
     */
    @Transactional
    fun updateOnboardingResource(
        resourceId: UUID,
        request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse {
        val resource = onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id: $resourceId") }

        resource.title = request.title
        resource.description = request.description
        resource.url = request.url

        return onboardingResourceRepository.save(resource).toUpdateResponse()
    }

    /**
     * Deletes an onboarding resource by its ID.
     *
     * @param resourceId The ID of the resource to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no resource exists with [resourceId].
     */
    @Transactional
    fun deleteOnboardingResource(resourceId: UUID) {
        val resource = onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id: $resourceId") }

        onboardingResourceRepository.delete(resource)
    }
}
