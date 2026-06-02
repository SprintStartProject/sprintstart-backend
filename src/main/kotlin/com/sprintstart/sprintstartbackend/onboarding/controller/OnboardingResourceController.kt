package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
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
 * REST controller for managing onboarding resources.
 *
 * Exposes endpoints under `/api/v1/onboarding` for creating, retrieving, updating,
 * and deleting onboarding resources. Resources are reference materials (e.g. documentation
 * links, videos) attached to a step. They are leaf nodes in the onboarding hierarchy
 * and have no children.
 *
 * Unlike phases, steps, and tasks, resources are unordered — they have no position field
 * and are not subject to any reordering on create, update, or delete.
 *
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - Resources", description = "Create, retrieve, update, and delete onboarding resources")
class OnboardingResourceController(
    val onboardingService: OnboardingService,
) {
    /**
     * Creates a new resource (reference link) attached to the specified step.
     *
     * Resources are unordered reference materials (e.g. documentation links, videos)
     * associated with a step. Unlike phases, steps, and tasks, resources have no
     * position field and are not subject to reordering.
     *
     * @param stepId The UUID of the step to attach the resource to.
     * @param request The resource creation request containing title, description, and URL.
     * @return The created resource.
     */
    @Operation(
        summary = "Create onboarding resource",
        description = "Creates a new reference resource (e.g. a documentation link or video) " +
            "attached to the specified step. Resources are unordered — unlike phases, steps, and" +
            " tasks, they have no position field.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Resource created successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/steps/{stepId}/resources")
    fun createOnboardingResourceForStepId(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
        @RequestBody request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse {
        return onboardingService.createOnboardingResourceForStepId(stepId, request)
    }

    /**
     * Returns all onboarding resources across all steps.
     *
     * This is a flat listing intended for administrative use. Resources are leaf nodes
     * with no nested content.
     *
     * @return A flat list of all onboarding resources.
     */
    @Operation(
        summary = "Get all onboarding resources",
        description = "Returns a flat list of all onboarding resources across all steps." +
            " Resources are leaf nodes — no nested content exists.",
    )
    @ApiResponse(responseCode = "200", description = "Flat list of all onboarding resources")
    @GetMapping("/resources")
    fun getOnboardingResources(): List<GetOnboardingResourcesResponse> {
        return onboardingService.getOnboardingResources()
    }

    /**
     * Returns all resources attached to a specific step.
     *
     * Resources are leaf nodes with no children and are unordered, so the response is
     * a flat list. The order of results is not guaranteed.
     *
     * @param stepId The UUID of the onboarding step.
     * @return A list of resources attached to the given step.
     */
    @Operation(
        summary = "Get resources by step ID",
        description = "Returns all onboarding resources attached to the specified step. " +
            "Resources are unordered leaf nodes — no nested content exists and result order is not guaranteed.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of resources for the step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @GetMapping("/steps/{stepId}/resources")
    fun getOnboardingResourcesByStepId(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
    ): List<GetOnboardingResourceResponse> {
        return onboardingService.getOnboardingResourceByStepId(stepId)
    }

    /**
     * Returns a single onboarding resource by its ID.
     *
     * Resources are leaf nodes with no children. The response contains only the
     * resource's own fields: title, description, and URL.
     *
     * @param resourceId The UUID of the onboarding resource.
     * @return The onboarding resource.
     */
    @Operation(
        summary = "Get onboarding resource by ID",
        description = "Returns a single onboarding resource by its UUID." +
            " Resources are leaf nodes — no nested content exists.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding resource found"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @GetMapping("/resources/{resourceId}")
    fun getOnboardingResource(
        @Parameter(description = "UUID of the onboarding resource") @PathVariable resourceId: UUID,
    ): GetOnboardingResourceResponse {
        return onboardingService.getOnboardingResource(resourceId)
    }

    /**
     * Updates an existing onboarding resource's title, description, and URL.
     *
     * All three fields are replaced with the values from the request. Resources have
     * no position and no status, so there are no ordering side-effects to consider.
     *
     * @param resourceId The UUID of the resource to update.
     * @param request The resource update request.
     * @return The updated resource.
     */
    @Operation(
        summary = "Update onboarding resource",
        description = "Replaces the title, description, and URL of an existing onboarding resource. " +
            "Resources have no position or status, so there are no ordering side-effects.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Resource updated successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @PutMapping("/resources/{resourceId}")
    fun updateOnboardingResource(
        @Parameter(description = "UUID of the onboarding resource to update") @PathVariable resourceId: UUID,
        @RequestBody request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse {
        return onboardingService.updateOnboardingResource(resourceId, request)
    }

    /**
     * Deletes an onboarding resource by its ID.
     *
     * Resources are unordered, so no sibling reordering is needed after deletion.
     * This operation is not reversible.
     *
     * @param resourceId The UUID of the resource to delete.
     */
    @Operation(
        summary = "Delete onboarding resource",
        description = "Permanently deletes the specified onboarding resource. " +
            "Resources are unordered, so no sibling reordering is needed after deletion.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Resource deleted successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/resources/{resourceId}")
    fun deleteOnboardingResourceForStepId(
        @Parameter(description = "UUID of the onboarding resource to delete") @PathVariable resourceId: UUID,
    ) {
        onboardingService.deleteOnboardingResource(resourceId)
    }
}
