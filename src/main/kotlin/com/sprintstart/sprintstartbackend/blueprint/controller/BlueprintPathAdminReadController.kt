package com.sprintstart.sprintstartbackend.blueprint.controller

import com.sprintstart.sprintstartbackend.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.blueprint.model.response.graphNode.GetBlueprintGraphResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.service.BlueprintGraphNodeService
import com.sprintstart.sprintstartbackend.blueprint.service.BlueprintPathService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes global administrative blueprint path reads.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. Mutating endpoints on the same
 * base path live in [BlueprintPathAdminWriteController].
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintPathAdminReadController(
    private val blueprintPathService: BlueprintPathService,
    private val blueprintGraphNodeService: BlueprintGraphNodeService,
) {
    /**
     * Returns blueprints.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns blueprints",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns blueprints successfully",
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
    @GetMapping
    fun getBlueprints(): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Global)
    }

    /**
     * Returns history by key.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param blueprintKey Stable key shared by all versions of a blueprint path.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns history by key",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns history by key successfully",
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
    @GetMapping("/{blueprintKey}")
    fun getBlueprintHistoryByBlueprintKey(
        @PathVariable blueprintKey: UUID,
    ): List<GetBlueprintPathResponse> {
        return blueprintPathService.getBlueprintPathHistoryByBlueprintKey(BlueprintScope.Global, blueprintKey)
    }

    /**
     * Returns graph by path id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param pathId Blueprint path identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns graph by path id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns graph by path id successfully",
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
    @GetMapping("paths/{pathId}/graph")
    fun getBlueprintGraphByPathId(
        @PathVariable pathId: UUID,
    ): GetBlueprintGraphResponse {
        return blueprintGraphNodeService.getGraph(BlueprintScope.Global, pathId)
    }

    /**
     * Returns path by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param pathId Blueprint path identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns path by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns path by id successfully",
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
    @GetMapping("/paths/{pathId}")
    fun getBlueprintPathById(
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.getBlueprintPathById(BlueprintScope.Global, pathId)
    }
}
