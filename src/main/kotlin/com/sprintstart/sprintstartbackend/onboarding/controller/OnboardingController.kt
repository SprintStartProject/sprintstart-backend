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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller exposing all onboarding management endpoints.
 *
 * The onboarding hierarchy is:
 * ```
 * Path (per user)
 *  └── Phase (ordered)
 *       └── Step (ordered, with status)
 *            ├── Task (ordered checklist)
 *            └── Resource (reference links)
 * ```
 *
 * All endpoints are prefixed with `/api/v1/onboarding`.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "Manage onboarding paths, phases, steps, tasks, and resources")
class OnboardingController(
    val onboardingService: OnboardingService,
) {
    // -------------------------------------------------------------------------
    // Paths
    // -------------------------------------------------------------------------

    /**
     * Returns all onboarding paths across all users.
     *
     * @return A list of all onboarding paths.
     */
    @Operation(summary = "Get all onboarding paths", description = "Returns all onboarding paths across all users.")
    @ApiResponse(responseCode = "200", description = "List of all onboarding paths")
    @GetMapping("/paths")
    fun getAllPaths(): List<GetOnboardingPathsResponse> {
        return onboardingService.getAllOnboardingPaths()
    }

    /**
     * Returns a single onboarding path by its ID.
     *
     * @param pathId The UUID of the onboarding path.
     * @return The onboarding path.
     */
    @Operation(summary = "Get onboarding path by ID", description = "Returns a single onboarding path by its UUID.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path found"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @GetMapping("/paths/{pathId}")
    fun getPath(
        @Parameter(description = "UUID of the onboarding path") @PathVariable pathId: UUID,
    ): GetOnboardingPathResponse {
        return onboardingService.getOnboardingPath(pathId)
    }

    /**
     * Returns the onboarding path for a specific user, including all nested phases, steps, tasks, and resources.
     *
     * @param userId The UUID of the user.
     * @return The user's onboarding path with full nested content.
     */
    @Operation(
        summary = "Get onboarding path for a user",
        description = "Returns the onboarding path for the specified user, including all nested phases, steps, tasks, and resources.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path found for the user"),
        ApiResponse(responseCode = "404", description = "No user or onboarding path found for the given user ID"),
    )
    @GetMapping("/{userId}/path")
    fun getPathForUser(
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
    ): GetOnboardingPathForUserResponse {
        return onboardingService.getOnboardingPathByUserId(userId)
    }

    /**
     * Deletes an onboarding path by its ID.
     *
     * @param pathId The UUID of the onboarding path to delete.
     */
    @Operation(summary = "Delete onboarding path by ID", description = "Deletes the onboarding path with the given UUID.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path deleted"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @DeleteMapping("/paths/{pathId}")
    fun deletePath(
        @Parameter(description = "UUID of the onboarding path to delete") @PathVariable pathId: UUID,
    ) {
        onboardingService.deleteOnboardingPathById(pathId)
    }

    /**
     * Deletes the onboarding path associated with a specific user.
     *
     * @param userId The UUID of the user whose onboarding path should be deleted.
     */
    @Operation(summary = "Delete onboarding path by user ID", description = "Deletes the onboarding path belonging to the specified user.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path deleted"),
        ApiResponse(responseCode = "404", description = "No user or onboarding path found for the given user ID"),
    )
    @DeleteMapping("/{userId}/path")
    fun deletePathByUserId(
        @Parameter(description = "UUID of the user whose onboarding path should be deleted") @PathVariable userId: UUID,
    ) {
        onboardingService.deleteOnboardingPathByUserId(userId)
    }

    // -------------------------------------------------------------------------
    // Phases
    // -------------------------------------------------------------------------

    /**
     * Creates a new phase within the specified path.
     *
     * Phases positioned at or after the requested position are shifted forward automatically.
     *
     * @param pathId The UUID of the path to add the phase to.
     * @param request The phase creation request.
     * @return The created phase.
     */
    @Operation(
        summary = "Create onboarding phase",
        description = "Creates a new phase within the specified path. Existing phases at or after the requested position are shifted forward.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Phase created successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @PostMapping("/paths/{pathId}/phases")
    fun createOnboardingPhase(
        @Parameter(description = "UUID of the onboarding path") @PathVariable pathId: UUID,
        @RequestBody request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        return onboardingService.createOnboardingPhaseForPathId(pathId, request)
    }

    /**
     * Returns all onboarding phases across all paths.
     *
     * @return A list of all onboarding phases.
     */
    @Operation(summary = "Get all onboarding phases", description = "Returns all onboarding phases across all paths.")
    @ApiResponse(responseCode = "200", description = "List of all onboarding phases")
    @GetMapping("/phases")
    fun getOnboardingPhases(): List<GetOnboardingPhasesResponse> {
        return onboardingService.getOnboardingPhases()
    }

    /**
     * Returns all phases belonging to the specified path.
     *
     * @param pathId The UUID of the onboarding path.
     * @return A list of phases for the given path, ordered by position.
     */
    @Operation(summary = "Get phases by path ID", description = "Returns all onboarding phases belonging to the specified path, ordered by position.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of phases for the path"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @GetMapping("/paths/{pathId}/phases")
    fun getAllOnboardingPhasesByPathId(
        @Parameter(description = "UUID of the onboarding path") @PathVariable pathId: UUID,
    ): List<GetOnboardingPhaseResponse> {
        return onboardingService.getOnboardingPhasesByPathId(pathId)
    }

    /**
     * Returns a single onboarding phase by its ID.
     *
     * @param phaseId The UUID of the onboarding phase.
     * @return The onboarding phase.
     */
    @Operation(summary = "Get onboarding phase by ID", description = "Returns a single onboarding phase by its UUID.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding phase found"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @GetMapping("/phases/{phaseId}")
    fun getOnboardingPhase(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
    ): GetOnboardingPhaseResponse {
        return onboardingService.getOnboardingPhase(phaseId)
    }

    /**
     * Updates an existing onboarding phase.
     *
     * If the position changes, sibling phases are shifted automatically to maintain ordering.
     *
     * @param phaseId The UUID of the phase to update.
     * @param request The phase update request.
     * @return The updated phase.
     */
    @Operation(
        summary = "Update onboarding phase",
        description = "Updates an existing onboarding phase. Sibling phases are reordered automatically if the position changes.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Phase updated successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @PutMapping("/phases/{phaseId}")
    fun updateOnboardingPhase(
        @Parameter(description = "UUID of the onboarding phase to update") @PathVariable phaseId: UUID,
        @RequestBody request: UpdateOnboardingPhaseRequest,
    ): UpdateOnboardingPhaseResponse {
        return onboardingService.updateOnboardingPhase(phaseId, request)
    }

    /**
     * Deletes an onboarding phase and reorders remaining sibling phases.
     *
     * @param phaseId The UUID of the phase to delete.
     */
    @Operation(
        summary = "Delete onboarding phase",
        description = "Deletes the specified onboarding phase. Subsequent sibling phases are shifted back to fill the gap.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Phase deleted successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @DeleteMapping("/phases/{phaseId}")
    fun deleteOnboardingPhaseForPathId(
        @Parameter(description = "UUID of the onboarding phase to delete") @PathVariable phaseId: UUID,
    ) {
        onboardingService.deleteOnboardingPhase(phaseId)
    }

    // -------------------------------------------------------------------------
    // Steps
    // -------------------------------------------------------------------------

    /**
     * Creates a new step within the specified phase.
     *
     * Steps at or after the requested position are shifted forward automatically.
     * The new step is initialized with status `WAITING`.
     *
     * @param phaseId The UUID of the phase to add the step to.
     * @param request The step creation request.
     * @return The created step.
     */
    @Operation(
        summary = "Create onboarding step",
        description = "Creates a new step within the specified phase. Existing steps at or after the requested position are shifted forward. Initial status is WAITING.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Step created successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @PostMapping("/phases/{phaseId}/steps")
    fun createOnboardingStep(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
        @RequestBody request: CreateOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        return onboardingService.createOnboardingStepForPhaseId(phaseId, request)
    }

    /**
     * Returns all onboarding steps across all phases.
     *
     * @return A list of all onboarding steps.
     */
    @Operation(summary = "Get all onboarding steps", description = "Returns all onboarding steps across all phases.")
    @ApiResponse(responseCode = "200", description = "List of all onboarding steps")
    @GetMapping("/steps")
    fun getOnboardingSteps(): List<GetOnboardingStepsResponse> {
        return onboardingService.getOnboardingSteps()
    }

    /**
     * Returns all steps belonging to the specified phase.
     *
     * @param phaseId The UUID of the onboarding phase.
     * @return A list of steps for the given phase, ordered by position.
     */
    @Operation(summary = "Get steps by phase ID", description = "Returns all onboarding steps belonging to the specified phase, ordered by position.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of steps for the phase"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @GetMapping("/phases/{phaseId}/steps")
    fun getOnboardingStepsForPhaseId(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
    ): List<GetOnboardingStepResponse> {
        return onboardingService.getOnboardingStepsByPhaseId(phaseId)
    }

    /**
     * Returns a single onboarding step by its ID.
     *
     * @param stepId The UUID of the onboarding step.
     * @return The onboarding step.
     */
    @Operation(summary = "Get onboarding step by ID", description = "Returns a single onboarding step by its UUID.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding step found"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @GetMapping("/steps/{stepId}")
    fun getOnboardingStep(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
    ): GetOnboardingStepResponse {
        return onboardingService.getOnboardingStep(stepId)
    }

    /**
     * Updates an existing onboarding step, including status transitions and repositioning.
     *
     * Status transition side-effects:
     * - `FINISHED` — records completion timestamp.
     * - `SKIPPED` — records completion timestamp and skip reason.
     * - `WAITING` — clears completion timestamp and skip reason.
     *
     * @param stepId The UUID of the step to update.
     * @param request The step update request.
     * @return The updated step.
     */
    @Operation(
        summary = "Update onboarding step",
        description = """Updates an existing onboarding step. Sibling steps are reordered if the position changes.
            Status transitions have side-effects: FINISHED records a completion timestamp, 
            SKIPPED records a completion timestamp and skip reason, WAITING clears both.""",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Step updated successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @PutMapping("/steps/{stepId}")
    fun updateOnboardingStep(
        @Parameter(description = "UUID of the onboarding step to update") @PathVariable stepId: UUID,
        @RequestBody request: UpdateOnboardingStepRequest,
    ): UpdateOnboardingStepResponse {
        return onboardingService.updateOnboardingStep(stepId, request)
    }

    /**
     * Deletes an onboarding step and reorders remaining sibling steps.
     *
     * @param stepId The UUID of the step to delete.
     */
    @Operation(
        summary = "Delete onboarding step",
        description = "Deletes the specified onboarding step. Subsequent sibling steps are shifted back to fill the gap.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Step deleted successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @DeleteMapping("/steps/{stepId}")
    fun deleteOnboardingStepForPhaseId(
        @Parameter(description = "UUID of the onboarding step to delete") @PathVariable stepId: UUID,
    ) {
        onboardingService.deleteOnboardingStep(stepId)
    }

    // -------------------------------------------------------------------------
    // Tasks
    // -------------------------------------------------------------------------

    /**
     * Creates a new task within the specified step.
     *
     * Tasks at or after the requested position are shifted forward automatically.
     *
     * @param stepId The UUID of the step to add the task to.
     * @param request The task creation request.
     * @return The created task.
     */
    @Operation(
        summary = "Create onboarding task",
        description = "Creates a new task within the specified step. Existing tasks at or after the requested position are shifted forward.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task created successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @PostMapping("/steps/{stepId}/tasks")
    fun createOnboardingTask(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
        @RequestBody request: CreateOnboardingTaskRequest,
    ): CreateOnboardingTaskResponse {
        return onboardingService.createOnboardingTaskForStepId(stepId, request)
    }

    /**
     * Returns all onboarding tasks across all steps.
     *
     * @return A list of all onboarding tasks.
     */
    @Operation(summary = "Get all onboarding tasks", description = "Returns all onboarding tasks across all steps.")
    @ApiResponse(responseCode = "200", description = "List of all onboarding tasks")
    @GetMapping("/tasks")
    fun getOnboardingTasks(): List<GetOnboardingTasksResponse> {
        return onboardingService.getOnboardingTasks()
    }

    /**
     * Returns all tasks belonging to the specified step.
     *
     * @param stepId The UUID of the onboarding step.
     * @return A list of tasks for the given step, ordered by position.
     */
    @Operation(summary = "Get tasks by step ID", description = "Returns all onboarding tasks belonging to the specified step, ordered by position.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of tasks for the step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @GetMapping("/steps/{stepId}/tasks")
    fun getOnboardingTasksByStepId(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
    ): List<GetOnboardingTaskResponse> {
        return onboardingService.getOnboardingTasksByStepId(stepId)
    }

    /**
     * Returns a single onboarding task by its ID.
     *
     * @param taskId The UUID of the onboarding task.
     * @return The onboarding task.
     */
    @Operation(summary = "Get onboarding task by ID", description = "Returns a single onboarding task by its UUID.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding task found"),
        ApiResponse(responseCode = "404", description = "No onboarding task found with the given ID"),
    )
    @GetMapping("/tasks/{taskId}")
    fun getOnboardingTask(
        @Parameter(description = "UUID of the onboarding task") @PathVariable taskId: UUID,
    ): GetOnboardingTaskResponse {
        return onboardingService.getOnboardingTask(taskId)
    }

    /**
     * Updates an existing onboarding task, including repositioning it within its step.
     *
     * @param taskId The UUID of the task to update.
     * @param request The task update request.
     * @return The updated task.
     */
    @Operation(
        summary = "Update onboarding task",
        description = "Updates an existing onboarding task. Sibling tasks are reordered automatically if the position changes.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task updated successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding task found with the given ID"),
    )
    @PutMapping("/tasks/{taskId}")
    fun updateOnboardingTask(
        @Parameter(description = "UUID of the onboarding task to update") @PathVariable taskId: UUID,
        @RequestBody request: UpdateOnboardingTaskRequest,
    ): UpdateOnboardingTaskResponse {
        return onboardingService.updateOnboardingTask(taskId, request)
    }

    /**
     * Deletes an onboarding task and reorders remaining sibling tasks.
     *
     * @param taskId The UUID of the task to delete.
     */
    @Operation(
        summary = "Delete onboarding task",
        description = "Deletes the specified onboarding task. Subsequent sibling tasks are shifted back to fill the gap.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Task deleted successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding task found with the given ID"),
    )
    @DeleteMapping("/tasks/{taskId}")
    fun deleteOnboardingTaskForStepId(
        @Parameter(description = "UUID of the onboarding task to delete") @PathVariable taskId: UUID,
    ) {
        onboardingService.deleteOnboardingTask(taskId)
    }

    // -------------------------------------------------------------------------
    // Resources
    // -------------------------------------------------------------------------

    /**
     * Creates a new resource (reference link) attached to the specified step.
     *
     * @param stepId The UUID of the step to attach the resource to.
     * @param request The resource creation request.
     * @return The created resource.
     */
    @Operation(
        summary = "Create onboarding resource",
        description = "Creates a new reference resource (link) attached to the specified step.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Resource created successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @PostMapping("/steps/{stepId}/resources")
    fun createOnboardingResourceForStepId(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
        @RequestBody request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse {
        return onboardingService.createOnboardingResourceForStepId(stepId, request)
    }

    /**
     * Returns all onboarding resources across all steps.
     *
     * @return A list of all onboarding resources.
     */
    @Operation(summary = "Get all onboarding resources", description = "Returns all onboarding resources across all steps.")
    @ApiResponse(responseCode = "200", description = "List of all onboarding resources")
    @GetMapping("/resources")
    fun getOnboardingResources(): List<GetOnboardingResourcesResponse> {
        return onboardingService.getOnboardingResources()
    }

    /**
     * Returns all resources attached to the specified step.
     *
     * @param stepId The UUID of the onboarding step.
     * @return A list of resources for the given step.
     */
    @Operation(summary = "Get resources by step ID", description = "Returns all onboarding resources attached to the specified step.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of resources for the step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @GetMapping("/steps/{stepId}/resources")
    fun getOnboardingResourcesByStepId(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
    ): List<GetOnboardingResourceResponse> {
        return onboardingService.getOnboardingResourceByStepId(stepId)
    }

    /**
     * Returns a single onboarding resource by its ID.
     *
     * @param resourceId The UUID of the onboarding resource.
     * @return The onboarding resource.
     */
    @Operation(summary = "Get onboarding resource by ID", description = "Returns a single onboarding resource by its UUID.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding resource found"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @GetMapping("/resources/{resourceId}")
    fun getOnboardingResource(
        @Parameter(description = "UUID of the onboarding resource") @PathVariable resourceId: UUID,
    ): GetOnboardingResourceResponse {
        return onboardingService.getOnboardingResource(resourceId)
    }

    /**
     * Updates an existing onboarding resource's title, description, and URL.
     *
     * @param resourceId The UUID of the resource to update.
     * @param request The resource update request.
     * @return The updated resource.
     */
    @Operation(summary = "Update onboarding resource", description = "Updates the title, description, and URL of an existing onboarding resource.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Resource updated successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @PutMapping("/resources/{resourceId}")
    fun updateOnboardingResource(
        @Parameter(description = "UUID of the onboarding resource to update") @PathVariable resourceId: UUID,
        @RequestBody request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse {
        return onboardingService.updateOnboardingResource(resourceId, request)
    }

    /**
     * Deletes an onboarding resource by its ID.
     *
     * @param resourceId The UUID of the resource to delete.
     */
    @Operation(summary = "Delete onboarding resource", description = "Deletes the specified onboarding resource.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Resource deleted successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @DeleteMapping("/resources/{resourceId}")
    fun deleteOnboardingResourceForStepId(
        @Parameter(description = "UUID of the onboarding resource to delete") @PathVariable resourceId: UUID,
    ) {
        onboardingService.deleteOnboardingResource(resourceId)
    }
}
