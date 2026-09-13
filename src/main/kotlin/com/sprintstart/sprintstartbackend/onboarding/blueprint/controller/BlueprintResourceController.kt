package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.CreateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.DeleteBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.UpdateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.CreateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.UpdateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintResourceService
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
 * Exposes global administrative blueprint resource endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintResourceAdminController(
    private val blueprintResourceService: BlueprintResourceService,
) {
    /**
     * Returns resources for step.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns resources for step",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns resources for step successfully",
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
    @GetMapping("/step/{stepId}/resources")
    fun getBlueprintResourcesForStep(
        @PathVariable stepId: UUID,
    ): List<GetBlueprintResourceResponse> {
        return blueprintResourceService.getBlueprintResourcesForStep(BlueprintScope.Global, stepId)
    }

    /**
     * Returns resource.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param resourceId Blueprint resource identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns resource",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns resource successfully",
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
    @GetMapping("/resources/{resourceId}")
    fun getBlueprintResource(
        @PathVariable resourceId: UUID,
    ): GetBlueprintResourceResponse {
        return blueprintResourceService.getBlueprintResourceById(BlueprintScope.Global, resourceId)
    }

    /**
     * Creates resource.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param stepId Blueprint step identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates resource",
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
    @PostMapping("/step/{stepId}/resources")
    fun createBlueprintResource(
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintResourceRequest,
    ): CreateBlueprintResourceResponse {
        return blueprintResourceService.createBlueprintResourceForStep(BlueprintScope.Global, stepId, request)
    }

    /**
     * Updates resource by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param resourceId Blueprint resource identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates resource by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates resource by id successfully",
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
    @PutMapping("/resources/{resourceId}")
    fun updateBlueprintResourceById(
        @PathVariable resourceId: UUID,
        @RequestBody request: UpdateBlueprintResourceRequest,
    ): UpdateBlueprintResourceResponse {
        return blueprintResourceService.updateBlueprintResourceById(BlueprintScope.Global, resourceId, request)
    }

    /**
     * Deletes resource by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param resourceId Blueprint resource identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     */
    @Operation(
        summary = "Deletes resource by id",
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
    @DeleteMapping("/resources/{resourceId}")
    fun deleteBlueprintResourceById(
        @PathVariable resourceId: UUID,
        @RequestBody request: DeleteBlueprintResourceRequest,
    ) {
        return blueprintResourceService.deleteBlueprintResourceById(BlueprintScope.Global, resourceId, request)
    }
}

/**
 * Exposes project-scoped blueprint resource endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintResourceController(
    private val blueprintResourceService: BlueprintResourceService,
) {
    /**
     * Returns resources for step.
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
        summary = "Returns resources for step",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns resources for step successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping("/step/{stepId}/resources")
    fun getBlueprintResourcesForStep(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
    ): List<GetBlueprintResourceResponse> {
        return blueprintResourceService.getBlueprintResourcesForStep(BlueprintScope.Project(projectId), stepId)
    }

    /**
     * Returns resource.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param resourceId Blueprint resource identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns resource",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns resource successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping("/resources/{resourceId}")
    fun getBlueprintResource(
        @PathVariable projectId: UUID,
        @PathVariable resourceId: UUID,
    ): GetBlueprintResourceResponse {
        return blueprintResourceService.getBlueprintResourceById(
            BlueprintScope.Project(projectId),
            resourceId,
        )
    }

    /**
     * Creates resource.
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
        summary = "Creates resource",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PostMapping("/step/{stepId}/resources")
    fun createBlueprintResource(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintResourceRequest,
    ): CreateBlueprintResourceResponse {
        return blueprintResourceService.createBlueprintResourceForStep(
            BlueprintScope.Project(projectId),
            stepId,
            request,
        )
    }

    /**
     * Updates resource by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param resourceId Blueprint resource identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates resource by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates resource by id successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PutMapping("/resources/{resourceId}")
    fun updateBlueprintResourceById(
        @PathVariable projectId: UUID,
        @PathVariable resourceId: UUID,
        @RequestBody request: UpdateBlueprintResourceRequest,
    ): UpdateBlueprintResourceResponse {
        return blueprintResourceService.updateBlueprintResourceById(
            BlueprintScope.Project(projectId),
            resourceId,
            request,
        )
    }

    /**
     * Deletes resource by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param resourceId Blueprint resource identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     */
    @Operation(
        summary = "Deletes resource by id",
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @DeleteMapping("/resources/{resourceId}")
    fun deleteBlueprintResourceById(
        @PathVariable projectId: UUID,
        @PathVariable resourceId: UUID,
        @RequestBody request: DeleteBlueprintResourceRequest,
    ) {
        return blueprintResourceService.deleteBlueprintResourceById(
            BlueprintScope.Project(projectId),
            resourceId,
            request,
        )
    }
}
