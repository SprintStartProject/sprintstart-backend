package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.CreateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.DeleteBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.CreateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.GetBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintTaskService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes global administrative blueprint task endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintTaskAdminController(
    private val blueprintTaskService: BlueprintTaskService,
) {
    /**
     * Returns tasks for step.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns tasks for step",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns tasks for step successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/steps/{stepId}/tasks")
    fun getBlueprintTasksForStep(
        @PathVariable stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return blueprintTaskService.getBlueprintTasksForStep(BlueprintScope.Global, stepId)
    }

    /**
     * Returns task by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param taskId Blueprint task identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns task by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns task by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/tasks/{taskId}")
    fun getTaskById(
        @PathVariable taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintTaskService.getBlueprintTaskById(BlueprintScope.Global, taskId)
    }

    /**
     * Creates task for step.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates task for step",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Blueprint resource created",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/steps/{stepId}/task")
    fun createTaskForStep(
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        return blueprintTaskService.createBlueprintTaskForStep(BlueprintScope.Global, stepId, request)
    }

    /**
     * Updates task by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param taskId Blueprint task identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates task by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates task by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/tasks/{taskId}")
    fun updateTaskById(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskRequest,
    ): UpdateBlueprintTaskResponse {
        return blueprintTaskService.updateBlueprintTaskById(BlueprintScope.Global, taskId, request)
    }

    /**
     * Updates task position by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param taskId Blueprint task identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates task position by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates task position by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/tasks/{taskId}/position")
    fun updateBlueprintTaskPositionById(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskPositionRequest,
    ): List<UpdateBlueprintTaskPositionResponse> {
        return blueprintTaskService.updateBlueprintTaskPositionById(BlueprintScope.Global, taskId, request)
    }

    /**
     * Deletes task by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param taskId Blueprint task identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     */
    @Operation(
        summary = "Deletes task by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Blueprint resource deleted",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/tasks/{taskId}")
    fun deleteTaskById(
        @PathVariable taskId: UUID,
        @RequestBody request: DeleteBlueprintTaskRequest,
    ) {
        blueprintTaskService.deleteBlueprintTaskById(BlueprintScope.Global, taskId, request)
    }
}

/**
 * Exposes project-scoped blueprint task endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintTaskController(
    private val blueprintTaskService: BlueprintTaskService,
) {
    /**
     * Returns tasks for step.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param stepId Blueprint step identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns tasks for step",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns tasks for step successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/steps/{stepId}/tasks")
    fun getBlueprintTasksForStep(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return blueprintTaskService.getBlueprintTasksForStep(BlueprintScope.Project(projectId), stepId)
    }

    /**
     * Returns task by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param taskId Blueprint task identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns task by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns task by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/tasks/{taskId}")
    fun getTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintTaskService.getBlueprintTaskById(BlueprintScope.Project(projectId), taskId)
    }

    /**
     * Creates task for step.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param stepId Blueprint step identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates task for step",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Blueprint resource created",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PostMapping("/steps/{stepId}/task")
    fun createTaskForStep(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        return blueprintTaskService.createBlueprintTaskForStep(
            BlueprintScope.Project(projectId),
            stepId,
            request,
        )
    }

    /**
     * Updates task by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param taskId Blueprint task identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates task by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates task by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/tasks/{taskId}")
    fun updateTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskRequest,
    ): UpdateBlueprintTaskResponse {
        return blueprintTaskService.updateBlueprintTaskById(
            BlueprintScope.Project(projectId),
            taskId,
            request,
        )
    }

    /**
     * Updates task position by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param taskId Blueprint task identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates task position by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates task position by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/tasks/{taskId}/position")
    fun updateBlueprintTaskPositionById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskPositionRequest,
    ): List<UpdateBlueprintTaskPositionResponse> {
        return blueprintTaskService.updateBlueprintTaskPositionById(
            BlueprintScope.Project(projectId),
            taskId,
            request,
        )
    }

    /**
     * Deletes task by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param taskId Blueprint task identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     */
    @Operation(
        summary = "Deletes task by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Blueprint resource deleted",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @DeleteMapping("/tasks/{taskId}")
    fun deleteTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: DeleteBlueprintTaskRequest,
    ) {
        blueprintTaskService.deleteBlueprintTaskById(BlueprintScope.Project(projectId), taskId, request)
    }
}
