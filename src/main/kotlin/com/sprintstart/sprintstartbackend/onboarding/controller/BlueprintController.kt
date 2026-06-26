package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintDiffResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.BlueprintResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.DraftListResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.GenerateBlueprintsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.blueprint.VersionListResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BlueprintService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
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
    @Operation(summary = "Generate blueprints", description = "Triggers AI blueprint generation for the given scopes")
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun generateBlueprints(
        @RequestBody request: GenerateBlueprintsRequest,
    ): GenerateBlueprintsResponse {
        return blueprintService.generateBlueprints(request.scopes)
    }

    @Operation(summary = "List drafts", description = "Lists all pending blueprint drafts")
    @GetMapping("/drafts")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun listDrafts(): DraftListResponse {
        return blueprintService.listDrafts()
    }

    @Operation(summary = "Get draft diff", description = "Returns the diff for a specific draft scope")
    @GetMapping("/drafts/{scope}/diff")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun getDraftDiff(
        @PathVariable scope: String,
    ): BlueprintDiffResponse {
        return blueprintService.getDraftDiff(scope)
    }

    @Operation(summary = "Approve draft", description = "Promotes a draft to the active blueprint")
    @PostMapping("/drafts/{scope}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun approveDraft(
        @PathVariable scope: String,
    ): BlueprintResponse {
        return blueprintService.approveDraft(scope)
    }

    @Operation(summary = "Discard draft", description = "Deletes a pending draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/drafts/{scope}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun discardDraft(
        @PathVariable scope: String,
    ) {
        blueprintService.discardDraft(scope)
    }

    @Operation(summary = "List versions", description = "Returns all blueprint versions for a scope")
    @GetMapping("/{scope}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun listVersions(
        @PathVariable scope: String,
    ): VersionListResponse {
        return blueprintService.listVersions(scope)
    }

    @Operation(summary = "Rollback blueprint", description = "Restores a previous blueprint version")
    @PostMapping("/{scope}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    suspend fun rollback(
        @PathVariable scope: String,
        @RequestBody request: RollbackBlueprintRequest,
    ): BlueprintResponse {
        return blueprintService.rollback(scope, request.version)
    }
}

data class GenerateBlueprintsRequest(
    val scopes: List<String>? = null,
)

data class RollbackBlueprintRequest(
    val version: String,
)
