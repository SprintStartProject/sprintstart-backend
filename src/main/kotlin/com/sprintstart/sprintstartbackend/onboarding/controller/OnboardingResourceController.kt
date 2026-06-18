package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingResourceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
 * Exposes onboarding resource endpoints.
 *
 * Resources are leaf nodes attached to steps. Nesting depth for the onboarding
 * hierarchy is `0 = path`, `1 = phases`, `2 = steps`, and `3 = tasks/resources`.
 * Resource endpoints therefore operate on leaf objects at depth 3.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Resources",
    description = "Create, retrieve, update, " +
        "and delete onboarding resources at hierarchy depth 3",
)
class OnboardingResourceController(
    val onboardingResourceService: OnboardingResourceService,
) {
//  ========================== Endpoints for users (/me/...) ==========================

    /**
     * Returns all resources for one step in the authenticated user's onboarding path.
     *
     * Resources are leaf nodes at depth 3 and have no nested children.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the parent step at depth 2.
     * @return All resources for the step.
     */
    @Operation(
        summary = "Get current user's onboarding resources by step",
        description = "Returns onboarding resources at hierarchy depth 3 for the authenticated user. " +
            "Resources are leaf nodes, so no nested content exists below them.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Resources returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding resources"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/steps/{stepId}/resources")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingResourcesForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the parent onboarding step")
        @PathVariable stepId: UUID,
    ): List<GetOnboardingResourcesResponse> {
        return onboardingResourceService.getOnboardingResourcesForMe(jwt.subject, stepId)
    }

    /**
     * Creates a resource for one step in the authenticated user's onboarding path.
     *
     * The created object is a leaf node at depth 3.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the parent step at depth 2.
     * @param request Resource creation payload.
     * @return The created resource.
     */
    @Operation(
        summary = "Create current user's onboarding resource",
        description = "Creates an onboarding resource at hierarchy depth 3 " +
            "for the authenticated user under the specified step at depth 2.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Resource created successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding resources"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/steps/{stepId}/resources")
    @PreAuthorize("hasRole('USER')")
    fun createOnboardingResourceForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the parent onboarding step")
        @PathVariable stepId: UUID,
        @RequestBody request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse {
        return onboardingResourceService.createOnboardingResourceForMe(jwt.subject, stepId, request)
    }

    /**
     * Returns one resource from the authenticated user's onboarding path.
     *
     * Resources are leaf nodes at depth 3 with no descendants.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param resourceId Identifier of the resource to return.
     * @return The requested resource.
     */
    @Operation(
        summary = "Get current user's onboarding resource",
        description = "Returns one onboarding resource at hierarchy depth 3 for the authenticated user. " +
            "Resources are leaf nodes, so no nested content exists below them.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Resource returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding resource"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding resource found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/resources/{resourceId}")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingResourceForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding resource")
        @PathVariable resourceId: UUID,
    ): GetOnboardingResourceResponse {
        return onboardingResourceService.getOnboardingResourceForMe(jwt.subject, resourceId)
    }

    /**
     * Updates one resource in the authenticated user's onboarding path.
     *
     * The target object is a leaf node at depth 3.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param resourceId Identifier of the resource to update.
     * @param request Resource update payload.
     * @return The updated resource.
     */
    @Operation(
        summary = "Update current user's onboarding resource",
        description = "Updates an onboarding resource at hierarchy depth 3 for the authenticated user. " +
            "Resources are leaf nodes with no nested descendants.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Resource updated successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding resource"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding resource found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/resources/{resourceId}")
    @PreAuthorize("hasRole('USER')")
    fun updateOnboardingResourceForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding resource to update")
        @PathVariable resourceId: UUID,
        @Valid @RequestBody request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse {
        return onboardingResourceService.updateOnboardingResourceForMe(jwt.subject, resourceId, request)
    }

    /**
     * Deletes one resource from the authenticated user's onboarding path.
     *
     * The deleted object is a leaf node at depth 3.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param resourceId Identifier of the resource to delete.
     */
    @Operation(
        summary = "Delete current user's onboarding resource",
        description = "Deletes an onboarding resource at hierarchy depth 3 for the authenticated user. " +
            "Resources are leaf nodes, so no deeper nesting is affected.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Resource deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding resource"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding resource found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/resources/{resourceId}")
    @PreAuthorize("hasRole('USER')")
    fun deleteOnboardingResourceForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding resource to delete")
        @PathVariable resourceId: UUID,
    ) {
        onboardingResourceService.deleteOnboardingResourceForMe(jwt.subject, resourceId)
    }

//  ========================== Endpoints for admins ==========================

    /**
     * Returns all resources attached to a specific step.
     *
     * Resources are leaf nodes at depth 3 with no children and are unordered, so the
     * response is a flat list. The order of results is not guaranteed.
     *
     * @param stepId The UUID of the onboarding step.
     * @return A list of resources attached to the given step.
     */
    @Operation(
        summary = "Get resources by step ID",
        description = "Returns all onboarding resources attached to the specified step. " +
            "Each returned resource is a leaf node at hierarchy depth 3. " +
            "No nested content exists and result order is not guaranteed.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "List of resources for the step"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding resources"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}/resources")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingResourcesByStepId(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
    ): List<GetOnboardingResourcesResponse> {
        return onboardingResourceService.getOnboardingResourcesByStepId(stepId)
    }

    /**
     * Creates a new resource (reference link) attached to the specified step.
     *
     * Resources are unordered reference materials (e.g. documentation links, videos)
     * associated with a step. Unlike phases, steps, and tasks, resources have no
     * position field and are not subject to reordering. The created object is a leaf node at depth 3.
     *
     * @param stepId The UUID of the step to attach the resource to.
     * @param request The resource creation request containing title, description, and URL.
     * @return The created resource.
     */
    @Operation(
        summary = "Create onboarding resource",
        description = "Creates a new reference resource (e.g. a documentation link or video) " +
            "attached to the specified step. Resources are unordered — unlike phases, steps, and" +
            " tasks, they have no position field. The created object is at hierarchy depth 3.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Resource created successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding resources"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/steps/{stepId}/resources")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createOnboardingResourceForStepId(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
        @RequestBody request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse {
        return onboardingResourceService.createOnboardingResourceForStepId(stepId, request)
    }

    /**
     * Returns a single onboarding resource by its ID.
     *
     * Resources are leaf nodes at depth 3 with no children. The response contains only the
     * resource's own fields: title, description, and URL.
     *
     * @param resourceId The UUID of the onboarding resource.
     * @return The onboarding resource.
     */
    @Operation(
        summary = "Get onboarding resource by ID",
        description = "Returns a single onboarding resource by its UUID." +
            " The resource is a leaf node at hierarchy depth 3, so no nested content exists.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding resource found"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding resource"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/resources/{resourceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingResourceById(
        @Parameter(description = "UUID of the onboarding resource") @PathVariable resourceId: UUID,
    ): GetOnboardingResourceResponse {
        return onboardingResourceService.getOnboardingResourceById(resourceId)
    }

    /**
     * Updates an existing onboarding resource's title, description, and URL.
     *
     * All three fields are replaced with the values from the request. Resources have
     * no position and no status, so there are no ordering side effects to consider.
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
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding resource"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/resources/{resourceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateOnboardingResourceById(
        @Parameter(description = "UUID of the onboarding resource to update") @PathVariable resourceId: UUID,
        @RequestBody request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse {
        return onboardingResourceService.updateOnboardingResourceById(resourceId, request)
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
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding resource"),
        ApiResponse(responseCode = "404", description = "No onboarding resource found with the given ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/resources/{resourceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteOnboardingResourceById(
        @Parameter(description = "UUID of the onboarding resource to delete") @PathVariable resourceId: UUID,
    ) {
        onboardingResourceService.deleteOnboardingResourceById(resourceId)
    }
}
