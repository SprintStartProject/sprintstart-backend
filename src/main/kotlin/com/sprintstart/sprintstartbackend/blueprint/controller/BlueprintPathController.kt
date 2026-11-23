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
 * Exposes the project-scoped blueprint path writes: create, draft, publish, roll back, update, archive and delete.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Every operation requires
 * `@projectAuth.canManageProject` — admins and the project's manager — while the service layer enforces
 * blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintPathController(
    private val blueprintPathService: BlueprintPathService,
) {
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
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
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintDraftById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ) {
        blueprintPathService.deleteBlueprintPathDraftById(BlueprintScope.Project(projectId), pathId)
    }
}
