package com.sprintstart.sprintstartbackend.blueprint.controller

import com.sprintstart.sprintstartbackend.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.UpdateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.service.BlueprintPathService
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
 * Exposes global administrative blueprint path mutations.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 * Reads on the same base path live in [BlueprintPathAdminReadController].
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintPathAdminWriteController(
    private val blueprintPathService: BlueprintPathService,
) {
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
