package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.GetBlueprintGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.UpdateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintGraphNodeService
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPathService
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
 * Exposes global administrative blueprint path endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintPathAdminController(
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

    /**
     * Creates path.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates path",
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
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/paths")
    fun createBlueprintPath(
        @RequestBody request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        return blueprintPathService.createBlueprintPath(BlueprintScope.Global, request)
    }

    /**
     * Handles edit path by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param blueprintKey Stable key shared by all versions of a blueprint path.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Handles edit path by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Handles edit path by id successfully",
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
    @PostMapping("{blueprintKey}/draft")
    fun editBlueprintPathById(
        @PathVariable blueprintKey: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Global, blueprintKey)
    }

    /**
     * Publishes path by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param pathId Blueprint path identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Publishes path by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Publishes path by id successfully",
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
    @PostMapping("/paths/{pathId}/publish")
    fun publishBlueprintPathById(
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.publishBlueprintPathDraftById(BlueprintScope.Global, pathId)
    }

    /**
     * Handles roll back path by key.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param blueprintKey Stable key shared by all versions of a blueprint path.
     *
     * @param rollbackVersion Endpoint input.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Handles roll back path by key",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Handles roll back path by key successfully",
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
                responseCode = "500",
                description = "Persisted blueprint version history violates its required invariants",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{blueprintKey}/rollBack/{rollbackVersion}")
    fun rollBackBlueprintPathByBlueprintKey(
        @PathVariable blueprintKey: UUID,
        @PathVariable rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        return blueprintPathService.rollbackBlueprintPathByBlueprintKey(
            BlueprintScope.Global,
            blueprintKey,
            rollbackVersion,
        )
    }

    /**
     * Updates path by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param pathId Blueprint path identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates path by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates path by id successfully",
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
    @PutMapping("/paths/{pathId}")
    fun updateBlueprintPathById(
        @PathVariable pathId: UUID,
        @RequestBody request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        return blueprintPathService.updateBlueprintPathById(BlueprintScope.Global, pathId, request)
    }

    // any unpublished drafts are deleted

    /**
     * Archives path by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param blueprintKey Stable key shared by all versions of a blueprint path.
     */
    @Operation(
        summary = "Archives path by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Archives path by id successfully",
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
    @PostMapping("/{blueprintKey}/archive")
    fun archiveBlueprintPathById(
        @PathVariable blueprintKey: UUID,
    ) {
        blueprintPathService.archiveBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey)
    }

    /**
     * Deletes draft by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param pathId Blueprint path identifier.
     */
    @Operation(
        summary = "Deletes draft by id",
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
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintDraftById(
        @PathVariable pathId: UUID,
    ) {
        blueprintPathService.deleteBlueprintPathDraftById(BlueprintScope.Global, pathId)
    }
}

/**
 * Exposes project-scoped blueprint path endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
@Suppress("TooManyFunctions")
class BlueprintPathController(
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/paths/{pathId}")
    fun getBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.getBlueprintPathById(BlueprintScope.Project(projectId), pathId)
    }

    /**
     * Creates path.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates path",
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
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/paths")
    fun createBlueprintPath(
        @PathVariable projectId: UUID,
        @RequestBody request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        return blueprintPathService.createBlueprintPath(BlueprintScope.Project(projectId), request)
    }

    /**
     * Handles edit path by id.
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
        summary = "Handles edit path by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Handles edit path by id successfully",
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
    @PostMapping("/{blueprintKey}/draft")
    fun editBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.openBlueprintPathDraftByBlueprintKey(
            BlueprintScope.Project(projectId),
            blueprintKey,
        )
    }

    /**
     * Publishes path by id.
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
        summary = "Publishes path by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Publishes path by id successfully",
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
    @PostMapping("/paths/{pathId}/publish")
    fun publishBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.publishBlueprintPathDraftById(BlueprintScope.Project(projectId), pathId)
    }

    /**
     * Handles roll back path by key.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param blueprintKey Stable key shared by all versions of a blueprint path.
     *
     * @param rollbackVersion Endpoint input.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Handles roll back path by key",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Handles roll back path by key successfully",
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
                responseCode = "500",
                description = "Persisted blueprint version history violates its required invariants",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/{blueprintKey}/rollBack/{rollbackVersion}")
    fun rollBackBlueprintPathByBlueprintKey(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
        @PathVariable rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        return blueprintPathService.rollbackBlueprintPathByBlueprintKey(
            BlueprintScope.Project(projectId),
            blueprintKey,
            rollbackVersion,
        )
    }

    /**
     * Updates path by id.
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
        summary = "Updates path by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates path by id successfully",
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
    @PutMapping("/paths/{pathId}")
    fun updateBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
        @RequestBody request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        return blueprintPathService.updateBlueprintPathById(BlueprintScope.Project(projectId), pathId, request)
    }

    // any unpublished drafts are deleted

    /**
     * Archives path by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param blueprintKey Stable key shared by all versions of a blueprint path.
     */
    @Operation(
        summary = "Archives path by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Archives path by id successfully",
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
    @PostMapping("/{blueprintKey}/archive")
    fun archiveBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ) {
        blueprintPathService.archiveBlueprintPathByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
    }

    /**
     * Deletes draft by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param pathId Blueprint path identifier.
     */
    @Operation(
        summary = "Deletes draft by id",
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
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintDraftById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ) {
        blueprintPathService.deleteBlueprintPathDraftById(BlueprintScope.Project(projectId), pathId)
    }
}
