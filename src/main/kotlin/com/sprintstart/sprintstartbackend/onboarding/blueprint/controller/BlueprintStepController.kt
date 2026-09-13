package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.CreateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.DeleteBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.CreateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.DeleteBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintStepService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
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
 * Exposes global administrative blueprint step endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintStepAdminController(
    private val blueprintStepService: BlueprintStepService,
) {
    /**
     * Returns steps for phase.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns steps for phase",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns steps for phase successfully",
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
    @GetMapping("/phases/{phaseId}/steps")
    fun getBlueprintStepsForPhase(
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintStepResponse> {
        return blueprintStepService.getBlueprintStepForPhase(BlueprintScope.Global, phaseId)
    }

    /**
     * Returns step by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns step by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns step by id successfully",
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
    @GetMapping("/steps/{stepId}")
    fun getBlueprintStepById(
        @PathVariable stepId: UUID,
    ): GetBlueprintStepResponse {
        return blueprintStepService.getBlueprintStepById(BlueprintScope.Global, stepId)
    }

    /**
     * Creates step for phase.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates step for phase",
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
    @PostMapping("/phases/{phaseId}/steps")
    fun createBlueprintStepForPhase(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintStepRequest,
    ): CreateBlueprintStepResponse {
        return blueprintStepService.createBlueprintStepForPhase(BlueprintScope.Global, phaseId, request)
    }

    /**
     * Updates step by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates step by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates step by id successfully",
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
    @PutMapping("/steps/{stepId}")
    fun updateBlueprintStepById(
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateBlueprintStepRequest,
    ): UpdateBlueprintStepResponse {
        return blueprintStepService.updateBlueprintStepById(BlueprintScope.Global, stepId, request)
    }

    /**
     * Updates step position by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates step position by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates step position by id successfully",
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
    @PutMapping("/steps/{stepId}/position")
    fun updateBlueprintStepPositionById(
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: UpdateBlueprintStepPositionRequest,
    ): List<UpdateBlueprintStepPositionResponse> {
        return blueprintStepService.updateBlueprintStepPositionById(BlueprintScope.Global, stepId, request)
    }

    /**
     * Deletes step by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Deletes step by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Deletes step by id successfully",
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
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/steps/{stepId}")
    fun deleteBlueprintStepById(
        @PathVariable stepId: UUID,
        @RequestBody request: DeleteBlueprintStepRequest,
    ): DeleteBlueprintStepResponse {
        return blueprintStepService.deleteBlueprintStepById(BlueprintScope.Global, stepId, request)
    }
}

/**
 * Exposes project-scoped blueprint step endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintStepController(
    private val blueprintStepService: BlueprintStepService,
) {
    /**
     * Returns steps for phase.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param phaseId Blueprint phase identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns steps for phase",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns steps for phase successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/phases/{phaseId}/steps")
    fun getBlueprintStepsForPhase(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintStepResponse> {
        return blueprintStepService.getBlueprintStepForPhase(BlueprintScope.Project(projectId), phaseId)
    }

    /**
     * Returns step by id.
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
        summary = "Returns step by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns step by id successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/steps/{stepId}")
    fun getBlueprintStepById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
    ): GetBlueprintStepResponse {
        return blueprintStepService.getBlueprintStepById(BlueprintScope.Project(projectId), stepId)
    }

    /**
     * Creates step for phase.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param phaseId Blueprint phase identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates step for phase",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/phases/{phaseId}/steps")
    fun createBlueprintStepForPhase(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintStepRequest,
    ): CreateBlueprintStepResponse {
        return blueprintStepService.createBlueprintStepForPhase(BlueprintScope.Project(projectId), phaseId, request)
    }

    /**
     * Updates step by id.
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
        summary = "Updates step by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates step by id successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/steps/{stepId}")
    fun updateBlueprintStepById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateBlueprintStepRequest,
    ): UpdateBlueprintStepResponse {
        return blueprintStepService.updateBlueprintStepById(BlueprintScope.Project(projectId), stepId, request)
    }

    /**
     * Updates step position by id.
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
        summary = "Updates step position by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates step position by id successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/steps/{stepId}/position")
    fun updateBlueprintStepPositionById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: UpdateBlueprintStepPositionRequest,
    ): List<UpdateBlueprintStepPositionResponse> {
        return blueprintStepService.updateBlueprintStepPositionById(
            BlueprintScope.Project(projectId),
            stepId,
            request,
        )
    }

    /**
     * Deletes step by id.
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
        summary = "Deletes step by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Deletes step by id successfully",
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
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/steps/{stepId}")
    fun deleteBlueprintStepById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: DeleteBlueprintStepRequest,
    ): DeleteBlueprintStepResponse {
        return blueprintStepService.deleteBlueprintStepById(BlueprintScope.Project(projectId), stepId, request)
    }
}
