package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingStepService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
 * Exposes onboarding step endpoints.
 *
 * Steps sit in the middle of the onboarding tree. Nesting depth for the onboarding
 * hierarchy is `0 = path`, `1 = phases`, `2 = steps`, and `3 = tasks/resources`.
 * Step endpoints therefore operate on depth-2 objects and may expose direct children
 * at depth 3.
 *
 * Steps are ordered within their parent phase by a numeric position. Insertions, updates,
 * and deletions automatically shift sibling steps to maintain a contiguous, gap-free ordering.
 *
 *
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Steps",
    description = "Create, retrieve, update, " +
        "and delete onboarding steps at hierarchy depth 2",
)
class OnboardingStepController(
    val onboardingStepService: OnboardingStepService,
) {
//  ========================== Endpoints for users (/me/...) ==========================

    /**
     * Returns all steps for one phase in the authenticated user's onboarding path.
     *
     * The returned objects are at depth 2. Each step may have direct children at depth 3:
     * tasks and resources.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the parent phase at depth 1.
     * @return All steps for the phase.
     */
    @Operation(
        summary = "Get current user's onboarding steps by phase",
        description = "Returns onboarding steps at hierarchy depth 2 for the authenticated user. " +
            "Each returned step may have direct children at depth 3: tasks and resources.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Steps returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding steps"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/phases/{phaseId}/steps")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingStepsForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the parent onboarding phase")
        @PathVariable phaseId: UUID,
    ): List<GetOnboardingStepsResponse> {
        return onboardingStepService.getOnboardingStepsForMe(jwt.subject, phaseId)
    }

    /**
     * Creates a step in one phase of the authenticated user's onboarding path.
     *
     * The created object is at depth 2 below the path and phase.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the parent phase at depth 1.
     * @param request Step creation payload.
     * @return The created step.
     */
    @Operation(
        summary = "Create current user's onboarding step",
        description = "Creates an onboarding step at hierarchy depth 2 " +
            "for the authenticated user under the specified phase at depth 1.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Step created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid step position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding steps"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding phase found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/phases/{phaseId}/steps")
    @PreAuthorize("hasRole('USER')")
    fun createOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the parent onboarding phase")
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        return onboardingStepService.createOnboardingStepForMe(jwt.subject, phaseId, request)
    }

    /**
     * Returns one step from the authenticated user's onboarding path.
     *
     * The returned object is at depth 2 and may include direct children at depth 3:
     * tasks and resources.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to return.
     * @return The requested step.
     */
    @Operation(
        summary = "Get current user's onboarding step",
        description = "Returns one onboarding step at hierarchy depth 2 for the authenticated user. " +
            "The maximum child depth below the step is depth 3 for tasks and resources.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding step"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("me/steps/{stepId}")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step")
        @PathVariable stepId: UUID,
    ): GetOnboardingStepResponse {
        return onboardingStepService.getOnboardingStepForMe(jwt.subject, stepId)
    }

    /**
     * Updates one step in the authenticated user's onboarding path.
     *
     * The target object is at depth 2. This endpoint only updates step metadata.
     * Position changes behave the same as the admin variant. Status changes are
     * handled by separate endpoints.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to update.
     * @param request Step update payload.
     * @return The updated step.
     */
    @Operation(
        summary = "Update current user's onboarding step",
        description = "Updates an onboarding step at hierarchy depth 2 for the authenticated user. " +
            "This endpoint updates step metadata only. Changing the position reorders sibling steps. " +
            "Status changes are handled by separate endpoints.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid step position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding step"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/steps/{stepId}")
    @PreAuthorize("hasRole('USER')")
    fun updateOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step to update")
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateOnboardingStepRequest,
    ): UpdateOnboardingStepResponse {
        return onboardingStepService.updateOnboardingStepForMe(jwt.subject, stepId, request)
    }

    /**
     * TODO: Add doc
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/steps/{stepId}/complete")
    @PreAuthorize("hasRole('USER')")
    fun completeOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step to update")
        @PathVariable stepId: UUID,
    ): UpdateOnboardingStepResponse {
        return onboardingStepService.completeOnboardingStepForMe(jwt.subject, stepId)
    }

    /**
     * Deletes one step from the authenticated user's onboarding path.
     *
     * The deleted object is at depth 2. Any descendants below it are at depth 3:
     * tasks and resources.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to delete.
     */
    @Operation(
        summary = "Delete current user's onboarding step",
        description = "Deletes an onboarding step at hierarchy depth 2 for the authenticated user. " +
            "Any descendants below the step are limited to depth 3: tasks and resources.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Step deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding step"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/steps/{stepId}")
    @PreAuthorize("hasRole('USER')")
    fun deleteOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step to delete")
        @PathVariable stepId: UUID,
    ) {
        onboardingStepService.deleteOnboardingStepForMe(jwt.subject, stepId)
    }

//  ========================== Endpoints for admins (/users/{userId}/path/phases/...) ==========================

    /**
     * Returns all steps belonging to a specific phase, including their direct tasks and resources.
     *
     * The returned objects are at depth 2. This response includes one additional child
     * level beneath each step: tasks and resources at depth 3. No deeper nesting exists.
     * Steps are ordered by their position within the phase.
     *
     * @param phaseId The UUID of the onboarding phase.
     * @return An ordered list of steps with their direct tasks and resources.
     */
    @Operation(
        summary = "Get steps by phase ID",
        description = "Returns all onboarding steps belonging to the specified phase, ordered by position. " +
            "Each returned step is at hierarchy depth 2 and includes direct children at depth 3: " +
            "tasks and resources. No deeper nesting exists.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Ordered list of steps with direct tasks and resources"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding steps"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/phases/{phaseId}/steps")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingStepsForPhaseId(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
    ): List<GetOnboardingStepResponse> {
        return onboardingStepService.getOnboardingStepsByPhaseId(phaseId)
    }

    /**
     * Creates a new step within the specified phase at the given position.
     *
     * Steps are ordered within a phase by a numeric position. If the requested position
     * is already occupied, all steps at or after that position are shifted forward by one.
     * The new step is always initialized with status [StepStatus.WAITING].
     *
     * @param phaseId The UUID of the phase to add the step to.
     * @param request The step creation request.
     * @return The created step.
     */
    @Operation(
        summary = "Create onboarding step",
        description = "Creates a new step within the specified phase at the given position. " +
            "Existing steps at or after the requested position are shifted forward by one. " +
            "The new step is always initialized with status WAITING.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Step created successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding steps"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/phases/{phaseId}/steps")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createOnboardingStep(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
        @RequestBody request: CreateOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        return onboardingStepService.createOnboardingStepForPhaseId(phaseId, request)
    }

    /**
     * Returns a single onboarding step by its ID, including its direct tasks and resources.
     *
     * The returned object is at depth 2. It includes direct children at depth 3:
     * tasks and resources. No deeper nesting exists below those leaf nodes.
     *
     * @param stepId The UUID of the onboarding step.
     * @return The onboarding step with its tasks and resources.
     */
    @Operation(
        summary = "Get onboarding step by ID",
        description = "Returns a single onboarding step by its UUID. " +
            "The step is at hierarchy depth 2 and includes direct children at depth 3: " +
            "tasks and resources. No deeper nesting exists.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding step with direct tasks and resources"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingStep(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
    ): GetOnboardingStepResponse {
        return onboardingStepService.getOnboardingStepById(stepId)
    }

    /**
     * Updates an existing onboarding step's metadata.
     *
     * All fields are replaced with the values from the request. If the position changes,
     * sibling steps are shifted automatically to maintain contiguous ordering. Status
     * changes are handled by separate endpoints.
     *
     * @param stepId The UUID of the step to update.
     * @param request The step update request.
     * @return The updated step.
     */
    @Operation(
        summary = "Update onboarding step",
        description = "Updates an existing onboarding step's metadata. " +
            "If the position changes, sibling steps are shifted automatically. " +
            "Status transitions are handled by separate endpoints.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Step updated successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateOnboardingStep(
        @Parameter(description = "UUID of the onboarding step to update") @PathVariable stepId: UUID,
        @RequestBody request: UpdateOnboardingStepRequest,
    ): UpdateOnboardingStepResponse {
        return onboardingStepService.updateOnboardingStepById(stepId, request)
    }

    /**
     * Deletes an onboarding step and reorders remaining siblings.
     *
     * After deletion, all steps in the same phase with a position greater than the
     * deleted step's position are shifted back by one. The deleted object is at depth 2,
     * and any descendants below it are leaf nodes at depth 3: tasks and resources.
     *
     * @param stepId The UUID of the step to delete.
     */
    @Operation(
        summary = "Delete onboarding step",
        description = "Permanently deletes the specified onboarding step. " +
            "Subsequent sibling steps are shifted back by one to keep ordering contiguous. " +
            "The deleted object is at hierarchy depth 2, and any descendants below it are " +
            "limited to depth 3: tasks and resources.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Step deleted successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteOnboardingStepById(
        @Parameter(description = "UUID of the onboarding step to delete") @PathVariable stepId: UUID,
    ) {
        onboardingStepService.deleteOnboardingStepById(stepId)
    }
}
