package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.AddBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.UpdateBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.AddBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.UpdateBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintGraphNodeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes global administrative blueprint graph endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprint/graph-nodes/{nodeId}")
class BlueprintGraphAdminController(
    private val blueprintGraphNodeService: BlueprintGraphNodeService,
) {
    /**
     * Adds graph node blocker.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param nodeId Graph node identifier.
     *
     * @param blockerId Identifier of the blocker node.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Adds graph node blocker",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Adds graph node blocker successfully",
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
    @PostMapping("/blockers/{blockerId}")
    fun addGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintGraphNodeBlockerRequest,
    ): AddBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.addGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Updates graph node position.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param nodeId Graph node identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates graph node position",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates graph node position successfully",
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
    @PutMapping("/position")
    fun updateGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintGraphNodePositionRequest,
    ): UpdateBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.updateGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }

    /**
     * Removes graph node blocker.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param nodeId Graph node identifier.
     *
     * @param blockerId Identifier of the blocker node.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Removes graph node blocker",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes graph node blocker successfully",
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
    @DeleteMapping("/blockers/{blockerId}")
    fun removeGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodeBlockerRequest,
    ): RemoveBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.removeGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Removes graph node position.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param nodeId Graph node identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Removes graph node position",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes graph node position successfully",
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
    @DeleteMapping("/position")
    fun removeGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodePositionRequest,
    ): RemoveBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.removeGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }
}

/**
 * Exposes project-scoped blueprint graph endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprint/graph-nodes/{nodeId}")
class BlueprintGraphController(
    private val blueprintGraphNodeService: BlueprintGraphNodeService,
) {
    /**
     * Adds graph node blocker.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param nodeId Graph node identifier.
     *
     * @param blockerId Identifier of the blocker node.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Adds graph node blocker",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Adds graph node blocker successfully",
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
    @PostMapping("/blockers/{blockerId}")
    fun addGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintGraphNodeBlockerRequest,
    ): AddBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.addGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Updates graph node position.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param nodeId Graph node identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates graph node position",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates graph node position successfully",
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
    @PutMapping("/position")
    fun updateGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintGraphNodePositionRequest,
    ): UpdateBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.updateGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }

    /**
     * Removes graph node blocker.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param nodeId Graph node identifier.
     *
     * @param blockerId Identifier of the blocker node.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Removes graph node blocker",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes graph node blocker successfully",
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
    @DeleteMapping("/blockers/{blockerId}")
    fun removeGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodeBlockerRequest,
    ): RemoveBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.removeGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Removes graph node position.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param nodeId Graph node identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Removes graph node position",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes graph node position successfully",
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
    @DeleteMapping("/position")
    fun removeGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodePositionRequest,
    ): RemoveBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.removeGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }
}
