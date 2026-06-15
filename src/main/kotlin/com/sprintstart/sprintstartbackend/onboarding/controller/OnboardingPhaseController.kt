package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPhaseService
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
 * Exposes onboarding phase endpoints.
 *
 * Phases are the first nested level below the onboarding path. Nesting depth for the
 * onboarding tree is `0 = path`, `1 = phases`, `2 = steps`, and `3 = tasks/resources`.
 * Phase endpoints therefore operate at depth 1 and may return child steps beneath them.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Phases",
    description = "Create, retrieve, update, and delete " +
        "onboarding phases at hierarchy depth 1",
)
class OnboardingPhaseController(
    val onboardingPhaseService: OnboardingPhaseService,
) {
//  ========================== Endpoints for users (/me/path/...) ==========================

    /**
     * Returns all phases in the authenticated user's onboarding path.
     *
     * Each returned item is a depth-1 phase. The maximum descendant depth below each
     * phase is 2 more levels: steps at depth 2 and tasks/resources at depth 3.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return All phases for the authenticated user.
     */
    @Operation(
        summary = "Get current user's onboarding phases",
        description = "Returns all onboarding phases at hierarchy depth 1 for the authenticated user. " +
            "Below each phase, the hierarchy can continue to steps (depth 2) and tasks/resources (depth 3).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Phases returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding phases"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding path found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/path/phases")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingPhasesForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): List<GetOnboardingPhasesResponse> {
        return onboardingPhaseService.getOnboardingPhasesForMe(jwt.subject)
    }

    /**
     * Creates a phase in the authenticated user's onboarding path.
     *
     * The created object lives at hierarchy depth 1 under the path root. Sibling phases
     * are reordered by position when necessary.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param request Phase creation payload.
     * @return The created phase.
     */
    @Operation(
        summary = "Create current user's onboarding phase",
        description = "Creates an onboarding phase at hierarchy depth 1 for the authenticated user. " +
            "Sibling phases are reordered as needed to keep positions contiguous.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Phase created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid phase position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding phases"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding path found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/path/phases")
    @PreAuthorize("hasRole('USER')")
    fun createOnboardingPhaseForUser(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        return onboardingPhaseService.createOnboardingPhaseForMe(jwt.subject, request)
    }

    /**
     * Returns one phase from the authenticated user's onboarding path.
     *
     * The returned object is at depth 1. Any nested content below it can only continue
     * to steps at depth 2 and tasks/resources at depth 3.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the phase to return.
     * @return The requested phase.
     */
    @Operation(
        summary = "Get current user's onboarding phase",
        description = "Returns one onboarding phase at hierarchy depth 1 for the authenticated user. " +
            "The maximum descendant depth below the phase is steps at depth 2 and tasks/resources at depth 3.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Phase returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding phase"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding phase found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/path/phases/{phaseId}")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingPhaseForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding phase")
        @PathVariable phaseId: UUID,
    ): GetOnboardingPhaseResponse {
        return onboardingPhaseService.getOnboardingPhaseForMe(jwt.subject, phaseId)
    }

    /**
     * Updates one phase in the authenticated user's onboarding path.
     *
     * The target object is at depth 1. Changing its position reorders sibling phases.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the phase to update.
     * @param request Phase update payload.
     * @return The updated phase.
     */
    @Operation(
        summary = "Update current user's onboarding phase",
        description = "Updates an onboarding phase at hierarchy depth 1 for the authenticated user. " +
            "Changing the position reorders sibling phases within the same path.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Phase updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid phase position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding phase"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding phase found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/path/phases/{phaseId}")
    @PreAuthorize("hasRole('USER')")
    fun updateOnboardingPhaseForUser(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding phase to update")
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateOnboardingPhaseRequest,
    ): UpdateOnboardingPhaseResponse {
        return onboardingPhaseService.updateOnboardingPhaseForMe(jwt.subject, phaseId, request)
    }

    /**
     * Deletes one phase from the authenticated user's onboarding path.
     *
     * The deleted object is at depth 1. Removing it also removes everything below it,
     * with steps at depth 2 and tasks/resources at depth 3.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the phase to delete.
     */
    @Operation(
        summary = "Delete current user's onboarding phase",
        description = "Deletes an onboarding phase at hierarchy depth 1 for the authenticated user. " +
            "Removing the phase also removes nested steps (depth 2) and tasks/resources (depth 3).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Phase deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding phase"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding phase found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/path/phases/{phaseId}")
    @PreAuthorize("hasRole('USER')")
    fun deleteOnboardingPhaseForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding phase to delete")
        @PathVariable phaseId: UUID,
    ) {
        onboardingPhaseService.deleteOnboardingPhaseForMe(jwt.subject, phaseId)
    }

//  ========================== Endpoints for admins (/users/{userId}/path/phases/...) ==========================

    /**
     * Returns all phases for a specific user's onboarding path.
     *
     * The returned objects are at depth 1 in the hierarchy.
     *
     * @param userId Identifier of the user whose phases should be returned.
     * @return All phases for the selected user.
     */
    @Operation(
        summary = "Get onboarding phases by user ID",
        description = "Returns all onboarding phases at hierarchy depth 1 for the specified user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Phases returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding phases"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/path/phases")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getAllOnboardingPhasesForUser(
        @Parameter(description = "UUID of the user whose phases should be returned")
        @PathVariable userId: UUID,
    ): List<GetOnboardingPhasesResponse> {
        return onboardingPhaseService.getOnboardingPhasesForUser(userId)
    }

    /**
     * Creates a phase for a specific user's onboarding path.
     *
     * The created object is at depth 1 below the user's path root.
     *
     * @param userId Identifier of the path owner.
     * @param request Phase creation payload.
     * @return The created phase.
     */
    @Operation(
        summary = "Create onboarding phase by user ID",
        description = "Creates an onboarding phase at hierarchy depth 1 for the specified user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Phase created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid phase position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding phases"),
            ApiResponse(responseCode = "404", description = "No onboarding path found for the given user"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/users/{userId}/path/phases")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createOnboardingPhaseForUser(
        @Parameter(description = "UUID of the user whose phase should be created")
        @PathVariable userId: UUID,
        @RequestBody request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        return onboardingPhaseService.createOnboardingPhaseForUserId(userId, request)
    }

    /**
     * Returns one onboarding phase by ID.
     *
     * The returned object is at depth 1. Any descendants below it can continue only
     * to steps at depth 2 and tasks/resources at depth 3.
     *
     * @param phaseId Identifier of the phase to return.
     * @return The requested phase.
     */
    @Operation(
        summary = "Get onboarding phase by ID",
        description = "Returns one onboarding phase at hierarchy depth 1. " +
            "The maximum descendant depth below the phase is steps at depth 2 and tasks/resources at depth 3.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Phase returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding phase"),
            ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/phases/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingPhaseForUser(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
    ): GetOnboardingPhaseResponse {
        return onboardingPhaseService.getOnboardingPhaseById(phaseId)
    }

    /**
     * Updates one onboarding phase by ID.
     *
     * The target object is at depth 1. Changing its position reorders sibling phases.
     *
     * @param phaseId Identifier of the phase to update.
     * @param request Phase update payload.
     * @return The updated phase.
     */
    @Operation(
        summary = "Update onboarding phase",
        description = "Updates an onboarding phase at hierarchy depth 1. " +
            "Changing the position reorders sibling phases within the same path.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Phase updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid phase position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding phase"),
            ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/phases/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateOnboardingPhaseForUser(
        @Parameter(description = "UUID of the onboarding phase to update") @PathVariable phaseId: UUID,
        @RequestBody request: UpdateOnboardingPhaseRequest,
    ): UpdateOnboardingPhaseResponse {
        return onboardingPhaseService.updateOnboardingPhaseById(phaseId, request)
    }

    /**
     * Deletes an onboarding phase and reorders remaining siblings.
     *
     * After deletion, all phases in the same path with a position greater than the
     * deleted phase's position are shifted back by one. The deleted object is at depth 1,
     * and all descendants below it are removed as well: steps at depth 2 and tasks/resources
     * at depth 3.
     *
     * @param phaseId The UUID of the phase to delete.
     */
    @Operation(
        summary = "Delete onboarding phase",
        description = "Permanently deletes the specified onboarding phase. " +
            "Subsequent sibling phases are shifted back by one to keep ordering contiguous. " +
            "The deleted object is at depth 1, and all descendant steps (depth 2)" +
            " and tasks/resources (depth 3) are removed via cascade.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Phase deleted successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding phase"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/phases/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteOnboardingPhaseForUser(
        @Parameter(description = "UUID of the onboarding phase to delete") @PathVariable phaseId: UUID,
    ) {
        onboardingPhaseService.deleteOnboardingPhaseById(phaseId)
    }
}
