package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
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
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController(
    val onboardingService: OnboardingService,
) {
    // Get: All paths
    @GetMapping("/paths")
    fun getAllPaths(): List<GetOnboardingPathsResponse> {
        return onboardingService.getAllOnboardingPaths()
    }

    @GetMapping("/paths/{pathId}")
    fun getPath(@PathVariable pathId: UUID): GetOnboardingPathResponse {
        return onboardingService.getOnboardingPath(pathId)
    }

    // Get: Path for user -> includes all upto step_id
    @GetMapping("/{userId}/path")
    fun getPathForUser(@PathVariable userId: UUID): GetOnboardingPathForUserResponse{
        return onboardingService.getOnboardingPathByUserId(userId)
    }

    // Delete: path by pathId
    @DeleteMapping("/paths/{pathId}")
    fun deletePath(@PathVariable pathId: UUID) {
        onboardingService.deleteOnboardingPathById(pathId)
    }

    @DeleteMapping("/{userId}/path")
    fun deletePathByUserId(@PathVariable userId: UUID) {
        onboardingService.deleteOnboardingPathByUserId(userId)
    }

    // Phase specific endpoints

    @PostMapping("/paths/{pathId}/phases")
    fun createOnboardingPhase(
        @PathVariable pathId: UUID,
        @RequestBody request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        return onboardingService.createOnboardingPhaseForPathId(pathId, request)
    }

    @GetMapping("/phases")
    fun getOnboardingPhases(): List<GetOnboardingPhasesResponse> {
        return onboardingService.getOnboardingPhases()
    }

    @GetMapping("/paths/{pathId}/phases")
    fun getAllOnboardingPhasesByPathId(@PathVariable pathId: UUID): List<GetOnboardingPhaseResponse> {
        return onboardingService.getOnboardingPhasesByPathId(pathId)
    }

    @GetMapping("/phases/{phaseId}")
    fun getOnboardingPhase(@PathVariable phaseId: UUID): GetOnboardingPhaseResponse {
        return onboardingService.getOnboardingPhase(phaseId)
    }

    @PutMapping("/phases/{phaseId}")
    fun updateOnboardingPhase(
        @PathVariable phaseId: UUID,
        @RequestBody request: UpdateOnboardingPhaseRequest,
    ): UpdateOnboardingPhaseResponse {
        return onboardingService.updateOnboardingPhase(phaseId, request)
    }

    @DeleteMapping("/phases/{phaseId}")
    fun deleteOnboardingPhaseForPathId(@PathVariable phaseId: UUID) {
        onboardingService.deleteOnboardingPhase(phaseId)
    }

    // Step specific Endpoints

    @PostMapping("/phases/{phaseId}/steps")
    fun createOnboardingStep(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        return onboardingService.createOnboardingStepForPhaseId(phaseId, request)
    }

    @GetMapping("/steps")
    fun getOnboardingSteps(): List<GetOnboardingStepsResponse> {
        return onboardingService.getOnboardingSteps()
    }

    @GetMapping("/phases/{phaseId}/steps")
    fun getOnboardingStepsForPhaseId(@PathVariable phaseId: UUID): List<GetOnboardingStepResponse> {
        return onboardingService.getOnboardingStepsByPhaseId(phaseId)
    }

    @GetMapping("/steps/{stepId}")
    fun getOnboardingStep(@PathVariable stepId: UUID): GetOnboardingStepResponse {
        return onboardingService.getOnboardingStep(stepId)
    }

    @PutMapping("/steps/{stepId}")
    fun updateOnboardingStep(
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateOnboardingStepRequest,
    ): UpdateOnboardingStepResponse {
        return onboardingService.updateOnboardingStep(stepId, request)
    }

    @DeleteMapping("/steps/{stepId}")
    fun deleteOnboardingStepForPhaseId(@PathVariable stepId: UUID) {
        onboardingService.deleteOnboardingStep(stepId)
    }

    // Task Specific Endpoints

    @PostMapping("/step/{stepId}/tasks")
    fun createOnboardingTask(
        @PathVariable stepId: UUID,
        @RequestBody request: CreateOnboardingTaskRequest,
    ): CreateOnboardingTaskResponse {
        return onboardingService.createOnboardingTaskForStepId(stepId, request)
    }

    @GetMapping("/tasks")
    fun getOnboardingTasks(): List<GetOnboardingTasksResponse> {
        return onboardingService.getOnboardingTasks()
    }

    @GetMapping("/steps/{stepId}/tasks")
    fun getOnboardingTasksByStepId(@PathVariable stepId: UUID): List<GetOnboardingTaskResponse> {
        return onboardingService.getOnboardingTasksByStepId(stepId)
    }

    @GetMapping("/tasks/{taskId}")
    fun getOnboardingTask(@PathVariable taskId: UUID): GetOnboardingTaskResponse {
        return onboardingService.getOnboardingTask(taskId)
    }

    @PutMapping("/tasks/{taskId}")
    fun updateOnboardingTask(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateOnboardingTaskRequest,
    ): UpdateOnboardingTaskResponse {
        return onboardingService.updateOnboardingTask(taskId, request)
    }

    @DeleteMapping("/tasks/{taskId}")
    fun deleteOnboardingTaskForStepId(@PathVariable taskId: UUID) {
        return onboardingService.deleteOnboardingTask(taskId)
    }

    // Resource specific Endpoints

    @PostMapping("/steps/{stepId}/resources")
    fun createOnboardingResourceForStepId(@PathVariable stepId: UUID, request: CreateOnboardingResourceRequest): CreateOnboardingResourceResponse {
        return onboardingService.createOnboardingResourceForStepId(stepId, request)
    }

    @GetMapping("/resources")
    fun getOnboardingResources(): List<GetOnboardingResourcesResponse> {
        return onboardingService.getOnboardingResources()
    }

    @GetMapping("/steps/{stepId}/resources")
    fun getOnboardingResourcesByStepId(@PathVariable stepId: UUID): List<GetOnboardingResourceResponse> {
        return onboardingService.getOnboardingResourceByStepId(stepId)
    }

    @GetMapping("/resources/{resourceId}")
    fun getOnboardingResource(@PathVariable resourceId: UUID): GetOnboardingResourceResponse {
        return onboardingService.getOnboardingResource(resourceId)
    }

    @PutMapping("/resources/{resourceId}")
    fun createOnboardingResourceForStepId(@PathVariable resourceId: UUID, request: UpdateOnboardingResourceRequest): UpdateOnboardingResourceResponse {
        return onboardingService.updateOnboardingResource(resourceId, request)
    }

    @DeleteMapping("/resources/{resourceId}")
    fun deleteOnboardingResourceForStepId(@PathVariable resourceId: UUID) {
        return onboardingService.deleteOnboardingResource(resourceId)
    }
}
