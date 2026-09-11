package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.CreateBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.DeleteBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPhaseRequirementService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes global administrative blueprint phase requirement endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("api/v1/onboarding/blueprints/")
class BlueprintPhaseRequirementAdminController(
    private val blueprintPhaseRequirementService: BlueprintPhaseRequirementService,
) {
    /**
     * Adds requirement list.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Adds requirement list",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Adds requirement list successfully",
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
    @PostMapping("/phases/{phaseId}/requirements")
    fun addRequirementList(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintPhaseRequirementsRequest,
    ): CreateBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.createBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Global,
            phaseId,
            request,
        )
    }

    /**
     * Deletes requirement list.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Deletes requirement list",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Deletes requirement list successfully",
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
    @DeleteMapping("/phases/{phaseId}/requirements")
    fun deleteRequirementList(
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequirementsRequest,
    ): DeleteBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.deleteBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Global,
            phaseId,
            request,
        )
    }
}

/**
 * Exposes project-scoped blueprint phase requirement endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("api/v1/projects/{projectId}/onboarding/blueprints/")
class BlueprintPhaseRequirementController(
    private val blueprintPhaseRequirementService: BlueprintPhaseRequirementService,
) {
    /**
     * Adds requirement list.
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
        summary = "Adds requirement list",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Adds requirement list successfully",
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
    @PostMapping("/phases/{phaseId}/requirements")
    fun addRequirementList(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintPhaseRequirementsRequest,
    ): CreateBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.createBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Project(projectId),
            phaseId,
            request,
        )
    }

    /**
     * Deletes requirement list.
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
        summary = "Deletes requirement list",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Deletes requirement list successfully",
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
    @DeleteMapping("/phases/{phaseId}/requirements")
    fun deleteRequirementList(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequirementsRequest,
    ): DeleteBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.deleteBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Project(projectId),
            phaseId,
            request,
        )
    }
}
