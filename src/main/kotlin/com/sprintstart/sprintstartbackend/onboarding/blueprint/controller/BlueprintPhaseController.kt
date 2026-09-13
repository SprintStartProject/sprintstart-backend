package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.DeleteBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhasePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhasePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.GetBlueprintSubGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPhaseService
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintSubGraphNodeService
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
 * Exposes global administrative blueprint phase endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintPhaseAdminController(
    private val blueprintPhaseService: BlueprintPhaseService,
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
) {
    /**
     * Returns phases for path.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param pathId Blueprint path identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns phases for path",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns phases for path successfully",
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
    @GetMapping("/path/{pathId}/phases")
    fun getBlueprintPhasesForPath(
        @PathVariable pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseService.getBlueprintPhasesForPath(BlueprintScope.Global, pathId)
    }

    /**
     * Returns sub graph for phase id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns sub graph for phase id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns sub graph for phase id successfully",
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
    @GetMapping("/phase/{phaseId}/graph")
    fun getBlueprintSubGraphForPhaseId(
        @PathVariable phaseId: UUID,
    ): GetBlueprintSubGraphResponse {
        return blueprintSubGraphNodeService.getSubGraph(BlueprintScope.Global, phaseId)
    }

    /**
     * Returns phase by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns phase by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns phase by id successfully",
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
    @GetMapping("/phases/{phaseId}")
    fun getBlueprintPhaseById(
        @PathVariable phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintPhaseService.getBlueprintPhaseById(BlueprintScope.Global, phaseId)
    }

    /**
     * Creates phase for path.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param pathId Blueprint path identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates phase for path",
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
    @PostMapping("/path/{pathId}/phases")
    fun createBlueprintPhaseForPath(
        @PathVariable pathId: UUID,
        @Valid @RequestBody request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        return blueprintPhaseService.createBlueprintPhaseForPath(BlueprintScope.Global, pathId, request)
    }

    /**
     * Updates phase by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates phase by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates phase by id successfully",
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
    @PutMapping("/phases/{phaseId}")
    fun updateBlueprintPhaseById(
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        return blueprintPhaseService.updateBlueprintPhaseById(BlueprintScope.Global, phaseId, request)
    }

    /**
     * Updates phase position by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates phase position by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates phase position by id successfully",
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
    @PutMapping("/phases/{phaseId}/position")
    fun updateBlueprintPhasePositionById(
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        return blueprintPhaseService.updateBlueprintPhasePositionById(BlueprintScope.Global, phaseId, request)
    }

    /**
     * Deletes phase by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param phaseId Blueprint phase identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Deletes phase by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Deletes phase by id successfully",
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
    @DeleteMapping("/phases/{phaseId}")
    fun deleteBlueprintPhaseById(
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequest,
    ): DeleteBlueprintPhaseResponse {
        return blueprintPhaseService.deleteBlueprintPhaseById(BlueprintScope.Global, phaseId, request)
    }
}

/**
 * Exposes project-scoped blueprint phase endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintPhaseController(
    private val blueprintPhaseService: BlueprintPhaseService,
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
) {
    /**
     * Returns phases for path.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param pathId Blueprint path identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns phases for path",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns phases for path successfully",
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
    @GetMapping("/path/{pathId}/phases")
    fun getBlueprintPhasesForPath(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseService.getBlueprintPhasesForPath(BlueprintScope.Project(projectId), pathId)
    }

    /**
     * Creates phase for path.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param pathId Blueprint path identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates phase for path",
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
    @PostMapping("/path/{pathId}/phases")
    fun createBlueprintPhaseForPath(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
        @Valid @RequestBody request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        return blueprintPhaseService.createBlueprintPhaseForPath(BlueprintScope.Project(projectId), pathId, request)
    }

    /**
     * Returns sub graph for phase id.
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
        summary = "Returns sub graph for phase id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns sub graph for phase id successfully",
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
    @GetMapping("/phase/{phaseId}/graph")
    fun getBlueprintSubGraphForPhaseId(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
    ): GetBlueprintSubGraphResponse {
        return blueprintSubGraphNodeService.getSubGraph(BlueprintScope.Project(projectId), phaseId)
    }

    /**
     * Returns phase by id.
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
        summary = "Returns phase by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns phase by id successfully",
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
    @GetMapping("/phases/{phaseId}")
    fun getBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintPhaseService.getBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId)
    }

    /**
     * Updates phase by id.
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
        summary = "Updates phase by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates phase by id successfully",
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
    @PutMapping("/phases/{phaseId}")
    fun updateBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        return blueprintPhaseService.updateBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, request)
    }

    /**
     * Updates phase position by id.
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
        summary = "Updates phase position by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates phase position by id successfully",
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
    @PutMapping("/phases/{phaseId}/position")
    fun updateBlueprintPhasePositionById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        return blueprintPhaseService.updateBlueprintPhasePositionById(
            BlueprintScope.Project(projectId),
            phaseId,
            request,
        )
    }

    /**
     * Deletes phase by id.
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
        summary = "Deletes phase by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Deletes phase by id successfully",
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
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @DeleteMapping("/phases/{phaseId}")
    fun deleteBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequest,
    ): DeleteBlueprintPhaseResponse {
        return blueprintPhaseService.deleteBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, request)
    }
}
