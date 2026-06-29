package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.blueprint.GenerateBlueprintsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.blueprint.RollbackBlueprintRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.GenerateBlueprintsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.VersionListResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BlueprintService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
@Tag(name = "Onboarding - Blueprints", description = "Manage AI-generated onboarding blueprints")
class BlueprintController(
    private val blueprintService: BlueprintService,
) {
    /**
     * Triggers AI blueprint generation for the given scopes.
     *
     * Generated blueprints are created as ACTIVE directly — no separate draft step.
     * Any previously ACTIVE version for the same scope is archived for rollback.
     * If no scopes are provided, all known scopes are generated.
     */
    @Operation(summary = "Generate blueprints", description = "Triggers AI blueprint generation for the given scopes")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Blueprint generation outcomes returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun generateBlueprints(
        @RequestBody request: GenerateBlueprintsRequest,
    ): GenerateBlueprintsResponse {
        return blueprintService.generateBlueprints(request.scopes)
    }

    /**
     * Returns all archived blueprint versions for the given scope.
     */
    @Operation(summary = "List versions", description = "Returns all archived blueprint versions for a scope")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Version list returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No blueprint found for the given scope"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{scope}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun listVersions(
        @PathVariable scope: String,
    ): VersionListResponse {
        return blueprintService.listVersions(scope)
    }

    /**
     * Restores a previous blueprint version for the given scope, replacing the active blueprint.
     */
    @Operation(summary = "Rollback blueprint", description = "Restores a previous blueprint version")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Blueprint rolled back and active blueprint returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No blueprint or version found for the given scope"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{scope}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun rollback(
        @PathVariable scope: String,
        @RequestBody request: RollbackBlueprintRequest,
    ): BlueprintResponse {
        return blueprintService.rollback(scope, request.version)
    }
}
