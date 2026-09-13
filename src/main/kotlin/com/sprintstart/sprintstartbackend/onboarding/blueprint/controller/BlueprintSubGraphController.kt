package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.AddBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.UpdateBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.AddBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintSubGraphNodeService
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
 * Exposes global administrative blueprint sub graph endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("api/v1/onboarding/blueprints/sub-graph-nodes/{nodeId}")
class BlueprintSubGraphAdminController(
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
) {
    /**
     * Adds sub graph node blocker.
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
        summary = "Adds sub graph node blocker",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Adds sub graph node blocker successfully",
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
    fun addSubGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintSubGraphNodeBlockerRequest,
    ): AddBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.addSubGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Updates sub graph node position.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param nodeId Graph node identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates sub graph node position",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates sub graph node position successfully",
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
    fun updateSubGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintSubGraphNodePositionRequest,
    ): UpdateBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.updateSubGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }

    /**
     * Removes sub graph node blocker.
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
        summary = "Removes sub graph node blocker",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes sub graph node blocker successfully",
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
    fun removeSubGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodeBlockerRequest,
    ): RemoveBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Removes sub graph node position.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param nodeId Graph node identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Removes sub graph node position",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes sub graph node position successfully",
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
    fun removeSubGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodePositionRequest,
    ): RemoveBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }
}

/**
 * Exposes project-scoped blueprint sub graph endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("api/v1/projects/{projectId}/onboarding/blueprints/sub-graph-nodes/{nodeId}")
class BlueprintSubGraphController(
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
) {
    /**
     * Adds sub graph node blocker.
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
        summary = "Adds sub graph node blocker",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Adds sub graph node blocker successfully",
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
    fun addSubGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintSubGraphNodeBlockerRequest,
    ): AddBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.addSubGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Updates sub graph node position.
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
        summary = "Updates sub graph node position",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates sub graph node position successfully",
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
    fun updateSubGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintSubGraphNodePositionRequest,
    ): UpdateBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.updateSubGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }

    /**
     * Removes sub graph node blocker.
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
        summary = "Removes sub graph node blocker",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes sub graph node blocker successfully",
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
    fun removeSubGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodeBlockerRequest,
    ): RemoveBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    /**
     * Removes sub graph node position.
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
        summary = "Removes sub graph node position",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Removes sub graph node position successfully",
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
    fun removeSubGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodePositionRequest,
    ): RemoveBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }
}
