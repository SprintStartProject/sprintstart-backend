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
 * Exposes the project-scoped blueprint path reads: the overview, a key's history, one path and its graph.
 *
 * Routes derive [BlueprintScope.Project] from the path's project identifier. Every operation requires
 * `@projectAuth.canManageProject` — admins and the project's manager — while the service layer enforces
 * blueprint ownership. The writes that change a path live in [BlueprintPathController].
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintPathReadController(
    private val blueprintPathService: BlueprintPathService,
    private val blueprintGraphNodeService: BlueprintGraphNodeService,
) {
    // This should return based on the blueprintKey

    /**
     * Returns blueprints.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns blueprints",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    @GetMapping
    fun getBlueprints(
        @PathVariable projectId: UUID,
    ): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsGroupedByBlueprintKey(
            BlueprintScope.Project(projectId),
        )
    }

    /**
     * Returns history by key.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param blueprintKey Stable key shared by all versions of a blueprint path.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns history by key",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    @GetMapping("/{blueprintKey}")
    fun getBlueprintHistoryByBlueprintKey(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ): List<GetBlueprintPathResponse> {
        return blueprintPathService.getBlueprintPathHistoryByBlueprintKey(
            BlueprintScope.Project(projectId),
            blueprintKey,
        )
    }

    // This should probably not be used

    /**
     * Returns paths.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns paths",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns paths successfully",
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    @GetMapping("/paths")
    fun getBlueprintPaths(
        @PathVariable projectId: UUID,
    ): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsForProjectId(projectId)
    }

    /**
     * Returns graph by path id.
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
        summary = "Returns graph by path id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    @GetMapping("paths/{pathId}/graph")
    fun getBlueprintGraphByPathId(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintGraphResponse {
        return blueprintGraphNodeService.getGraph(BlueprintScope.Project(projectId), pathId)
    }

    /**
     * Returns path by id.
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
        summary = "Returns path by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    @GetMapping("/paths/{pathId}")
    fun getBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.getBlueprintPathById(BlueprintScope.Project(projectId), pathId)
    }
}
