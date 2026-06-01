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

@Service
class OnboardingService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val onboardingStepRepository: OnboardingStepRepository,
    private val onboardingTaskRepository: OnboardingTaskRepository,
    private val onboardingResourceRepository: OnboardingResourceRepository,
    private val userApi: UserApi,
) {
    // Paths
    fun createOnboardingPathForUser(userId: UUID): CreateOnboardingPathResponse {
        if (!userApi.exists(userId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        return onboardingPathRepository.save(OnboardingPath(userId = userId)).toCreateResponse()
    }

    fun getAllOnboardingPaths(): List<GetOnboardingPathsResponse> {
        return onboardingPathRepository.findAll().map {
            it.toGetAllResponse()
        }
    }

    fun getOnboardingPath(pathId: UUID): GetOnboardingPathResponse {
        return onboardingPathRepository
            .findById(pathId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No onboarding path found with id: $pathId") }
            .toGetResponse()
    }

    fun getOnboardingPathByUserId(userId: UUID): GetOnboardingPathForUserResponse {
        if (!userApi.exists(userId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        return onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found for user with id: $userId") }
            .toGetForUserResponse()
    }

    fun deleteOnboardingPathById(pathId: UUID) {
        onboardingPathRepository.deleteById(pathId)
    }

    fun deleteOnboardingPathByUserId(userId: UUID) {
        if (!userApi.exists(userId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        onboardingPathRepository.deleteByUserId(userId)
    }

    // Phases
    @Transactional
    fun createOnboardingPhaseForPathId(pathId: UUID, request: CreateOnboardingPhaseRequest): CreateOnboardingPhaseResponse {
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

    @Transactional(readOnly = true)
    fun getOnboardingPhases(): List<GetOnboardingPhasesResponse> {
        return onboardingPhaseRepository.findAll().map { phase -> phase.toGetAllResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingPhasesByPathId(pathId: UUID): List<GetOnboardingPhaseResponse> {
        if (!onboardingPathRepository.existsById(pathId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No path found with id: $pathId")
        return onboardingPhaseRepository.findAllByPath_Id(pathId).map { phase -> phase.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingPhase(phaseId: UUID): GetOnboardingPhaseResponse {
        return onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }
            .toGetResponse()
    }

    @Transactional
    fun updateOnboardingPhase(phaseId: UUID, request: UpdateOnboardingPhaseRequest): UpdateOnboardingPhaseResponse {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        var phasesToShift: MutableList<OnboardingPhase> = mutableListOf()
        if (phase.position < request.position) {
            // Shift from new position up to current position
            phasesToShift = onboardingPhaseRepository.findByPath_IdAndPositionBetween(phase.path.id, phase.position + 1, request.position)
            phasesToShift.forEach { it.position -= 1 }
        }
        if (phase.position > request.position) {
            phasesToShift = onboardingPhaseRepository.findByPath_IdAndPositionBetween(phase.path.id, request.position, phase.position - 1)
            phasesToShift.forEach { it.position += 1 }
        }
        onboardingPhaseRepository.saveAll(phasesToShift)

        phase.position = request.position
        phase.title = request.title
        phase.description = request.description

        return onboardingPhaseRepository.save(phase).toUpdateResponse()
    }

    @Transactional
    fun deleteOnboardingPhase(phaseId: UUID) {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        // Shift left
        val phasesToShift = onboardingPhaseRepository.findByPath_IdAndPositionGreaterThan(phase.path.id, phase.position)
        phasesToShift.forEach { it.position -= 1 }
        if (phasesToShift.isNotEmpty()) onboardingPhaseRepository.saveAll(phasesToShift)

        onboardingPhaseRepository.delete(phase)
    }

    // Steps
    @Transactional
    fun createOnboardingStepForPhaseId(phaseId: UUID, request: CreateOnboardingStepRequest): CreateOnboardingStepResponse {
        val phase = onboardingPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId") }

        // Shift right
        val stepsToShift = onboardingStepRepository.findByPhase_IdAndPositionGreaterThanEqual(phaseId, request.position)
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

    @Transactional(readOnly = true)
    fun getOnboardingSteps(): List<GetOnboardingStepsResponse> {
        return onboardingStepRepository
            .findAll()
            .map { step -> step.toGetAllResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingStepsByPhaseId(phaseId: UUID): List<GetOnboardingStepResponse> {
        if (!onboardingPhaseRepository.existsById(phaseId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No phase found with id: $phaseId")
        return onboardingStepRepository
            .findAllByPhase_Id(phaseId)
            .map { step -> step.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingStep(stepId: UUID): GetOnboardingStepResponse {
        return onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }
            .toGetResponse()
    }

    @Transactional
    fun updateOnboardingStep(stepId: UUID, request: UpdateOnboardingStepRequest): UpdateOnboardingStepResponse {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        var stepsToShift: MutableList<OnboardingStep> = mutableListOf()
        if (step.position < request.position) {
            // Shift from new position up to current position
            stepsToShift = onboardingStepRepository.findByPhase_IdAndPositionBetween(step.phase.id, step.position + 1, request.position)
            stepsToShift.forEach { it.position -= 1 }
        }
        if (step.position > request.position) {
            stepsToShift = onboardingStepRepository.findByPhase_IdAndPositionBetween(step.phase.id, request.position, step.position - 1)
            stepsToShift.forEach { it.position += 1 }
        }
        onboardingStepRepository.saveAll(stepsToShift)

        step.position = request.position
        step.title = request.title
        step.description = request.description
        step.type = request.type
        step.estimatedMinutes = request.estimatedMinutes
        step.expectedOutcome = request.expectedOutcome
        step.status = request.status

        if (step.status != request.status) {
            when (step.status) {
                StepStatus.FINISHED -> {
                    step.completedAt = Instant.now()
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

        return onboardingStepRepository.save(step).toUpdateResponse()
    }

    @Transactional
    fun deleteOnboardingStep(stepId: UUID) {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        // Shift left
        val stepsToShift = onboardingStepRepository.findByPhase_IdAndPositionGreaterThan(step.phase.id, step.position)
        stepsToShift.forEach { it.position -= 1 }
        onboardingStepRepository.saveAll(stepsToShift)

        onboardingStepRepository.delete(step)
    }

    // Tasks
    @Transactional
    fun createOnboardingTaskForStepId(stepId: UUID, request: CreateOnboardingTaskRequest): CreateOnboardingTaskResponse {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        val tasksToShift = onboardingTaskRepository.findByStep_IdAndPositionGreaterThanEqual(stepId, request.position)
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

    @Transactional(readOnly = true)
    fun getOnboardingTasks(): List<GetOnboardingTasksResponse> {
        return onboardingTaskRepository.findAll().map { task -> task.toGetAllResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingTasksByStepId(stepId: UUID): List<GetOnboardingTaskResponse> {
        if (!onboardingStepRepository.existsById(stepId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId")
        return onboardingTaskRepository.findAllByStep_Id(stepId).map { task -> task.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingTask(taskId: UUID): GetOnboardingTaskResponse {
        return onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }
            .toGetResponse()
    }

    @Transactional
    fun updateOnboardingTask(taskId: UUID, request: UpdateOnboardingTaskRequest): UpdateOnboardingTaskResponse {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        var tasksToShift: MutableList<OnboardingTask> = mutableListOf()
        if (task.position < request.position) {
            // Shift from new position up to current position
            tasksToShift = onboardingTaskRepository.findByStep_IdAndPositionBetween(task.step.id, task.position + 1, request.position)
            tasksToShift.forEach { it.position -= 1 }
        }
        if (task.position > request.position) {
            tasksToShift = onboardingTaskRepository.findByStep_IdAndPositionBetween(task.step.id, request.position, task.position - 1)
            tasksToShift.forEach { it.position += 1 }
        }
        onboardingTaskRepository.saveAll(tasksToShift)

        task.position = request.position
        task.title = request.title
        task.description = request.description
        task.finished = request.finished

        return onboardingTaskRepository.save(task).toUpdateResponse()
    }

    @Transactional
    fun deleteOnboardingTask(taskId: UUID) {
        val task = onboardingTaskRepository
            .findById(taskId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No task found with id: $taskId") }

        val tasksToShift = onboardingTaskRepository.findByStep_IdAndPositionGreaterThan(task.step.id, task.position)
        tasksToShift.forEach { it.position -= 1 }
        onboardingTaskRepository.saveAll(tasksToShift)

        onboardingTaskRepository.delete(task)
    }

    // Resources
    @Transactional
    fun createOnboardingResourceForStepId(stepId: UUID, request: CreateOnboardingResourceRequest): CreateOnboardingResourceResponse {
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

    @Transactional(readOnly = true)
    fun getOnboardingResources(): List<GetOnboardingResourcesResponse> {
        return onboardingResourceRepository
            .findAll()
            .map { resource -> resource.toGetAllResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingResourceByStepId(stepId: UUID): List<GetOnboardingResourceResponse> {
        return onboardingResourceRepository
            .findAllByStep_Id(stepId)
            .map { resource -> resource.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getOnboardingResource(resourceId: UUID): GetOnboardingResourceResponse {
        return onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id $resourceId") }
            .toGetResponse()
    }

    @Transactional
    fun updateOnboardingResource(resourceId: UUID, request: UpdateOnboardingResourceRequest): UpdateOnboardingResourceResponse {
        val resource = onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id: $resourceId") }

        resource.title = request.title
        resource.description = request.description
        resource.url = request.url

        return onboardingResourceRepository.save(resource).toUpdateResponse()
    }

    @Transactional
    fun deleteOnboardingResource(resourceId: UUID) {
        val resource = onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id: $resourceId") }

        onboardingResourceRepository.delete(resource)
    }
}
