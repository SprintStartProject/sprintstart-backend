package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.UpdateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetAllOnboardingSkipsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.ReviewOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingSkipService
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
 * Exposes onboarding skip endpoints.
 *
 * Skip requests belong to onboarding steps at hierarchy depth 2 and model requests
 * to skip a step. A newly created skip remains pending until an admin accepts or
 * denies it. Accepting a skip marks the parent step as skipped, while denying it
 * returns the step to waiting.
 */
@RestController
@RequestMapping("/api/v1/onboarding/me")
@Tag(
    name = "Onboarding - User controls for skips",
    description = "Create, retrieve, review, and delete onboarding skip requests",
)
class OnboardingSkipController(
    private val onboardingSkipService: OnboardingSkipService,
) {
    /**
     * Returns every skip request belonging to the authenticated user.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return All skips on the user's onboarding path.
     */
    @Operation(
        summary = "Get current user's onboarding skips",
        description = "Returns all onboarding skip requests belonging to the authenticated user," +
            " ordered by creation time.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/skips")
    @PreAuthorize("hasRole('USER')")
    fun getAllSkipsForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): List<GetOnboardingSkipResponse> {
        return onboardingSkipService.getAllSkipsForMe(jwt.subject)
    }

    /**
     * Returns all skip requests for one step on the authenticated user's onboarding path.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the parent step.
     * @return All skips attached to the requested step.
     */
    @Operation(
        summary = "Get current user's skips by step",
        description = "Returns all onboarding skip requests for one step belonging to the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}/skips")
    @PreAuthorize("hasRole('USER')")
    fun getSkipsByStepIdForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step")
        @PathVariable stepId: UUID,
    ): List<GetOnboardingSkipResponse> {
        return onboardingSkipService.getSkipsByStepIdForMe(jwt.subject, stepId)
    }

    /**
     * Returns one skip request belonging to the authenticated user.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param skipId Identifier of the skip to return.
     * @return The requested skip.
     */
    @Operation(
        summary = "Get current user's onboarding skip",
        description = "Returns one onboarding skip request belonging to the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding skip"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding skip found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/skips/{skipId}")
    @PreAuthorize("hasRole('USER')")
    fun getSkipByIdForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding skip")
        @PathVariable skipId: UUID,
    ): GetOnboardingSkipResponse {
        return onboardingSkipService.getSkipByIdForMe(jwt.subject, skipId)
    }

    /**
     * Creates a pending skip request for one step on the authenticated user's onboarding path.
     *
     * The target step must still be waiting and must not already have a pending skip request.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to attach the skip to.
     * @param request Skip creation payload.
     * @return The created skip request.
     */
    @Operation(
        summary = "Create current user's onboarding skip",
        description = "Creates a new pending skip request for one step belonging to the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Skip created successfully"),
            ApiResponse(responseCode = "400", description = "Step cannot receive a new pending skip"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding skips"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/steps/{stepId}/skips")
    @PreAuthorize("hasRole('USER')")
    fun createSkipAtStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step")
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: CreateOnboardingSkipRequest,
    ): CreateOnboardingSkipResponse {
        return onboardingSkipService.createOnboardingSkipForMe(jwt.subject, stepId, request)
    }

    /**
     * Updates the reason of one pending skip request belonging to the authenticated user.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param skipId Identifier of the skip to update.
     * @param request Skip update payload.
     * @return The parent step response including the latest skip state.
     */
    @Operation(
        summary = "Update current user's onboarding skip",
        description = "Updates the reason of a pending onboarding skip request belonging to the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip updated successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be updated"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update onboarding skips"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding skip found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/skips/{skipId}")
    @PreAuthorize("hasRole('USER')")
    fun updateSkipByIdForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding skip to update")
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: UpdateOnboardingSkipRequest,
    ): UpdateOnboardingStepResponse {
        return onboardingSkipService.updateOnboardingSkipForMe(jwt.subject, skipId, request)
    }

    /**
     * Deletes one skip request belonging to the authenticated user.
     *
     * Only pending skip requests can be deleted.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param skipId Identifier of the skip to delete.
     */
    @Operation(
        summary = "Delete current user's onboarding skip",
        description = "Deletes one onboarding skip request belonging to the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Skip deleted successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete onboarding skips"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding skip found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/skips/{skipId}")
    @PreAuthorize("hasRole('USER')")
    fun deleteSkipByIdForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding skip to delete")
        @PathVariable skipId: UUID,
    ) {
        onboardingSkipService.deleteSkipByIdForMe(jwt.subject, skipId)
    }
}

@RestController
@RequestMapping("/api/v1/admin/onboarding")
@Tag(
    name = "Onboarding - Admin controls for skips",
    description = "Create, retrieve, review, and delete onboarding skip requests",
)
class OnboardingSkipAdminController(
    private val onboardingSkipService: OnboardingSkipService,
) {
    /**
     * Returns every onboarding skip request in the system.
     *
     * @return All skip requests ordered by creation time.
     */
    @Operation(
        summary = "Get all onboarding skips",
        description = "Returns every onboarding skip request in the system.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/skips")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllSkips(): List<GetAllOnboardingSkipsResponse> {
        return onboardingSkipService.getAllSkips()
    }

    /**
     * Returns every onboarding skip request belonging to one user.
     *
     * @param userId Identifier of the user whose skips should be returned.
     * @return All skip requests on the requested user's onboarding path.
     */
    @Operation(
        summary = "Get onboarding skips by user ID",
        description = "Returns all onboarding skip requests belonging to the specified user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(responseCode = "404", description = "No user found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/skips")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllSkipsByUserId(
        @Parameter(description = "UUID of the user")
        @PathVariable userId: UUID,
    ): List<GetOnboardingSkipResponse> {
        return onboardingSkipService.getAllSkipsByUserId(userId)
    }

    /**
     * Returns every onboarding skip request attached to one step.
     *
     * @param stepId Identifier of the step whose skips should be returned.
     * @return All skip requests attached to the requested step.
     */
    @Operation(
        summary = "Get onboarding skips by step ID",
        description = "Returns all onboarding skip requests attached to the specified step.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}/skips")
    @PreAuthorize("hasRole('ADMIN')")
    fun getSkipsByStepId(
        @Parameter(description = "UUID of the onboarding step")
        @PathVariable stepId: UUID,
    ): List<GetOnboardingSkipResponse> {
        return onboardingSkipService.getAllSkipsByStepId(stepId)
    }

    /**
     * Returns one onboarding skip request by ID.
     *
     * @param skipId Identifier of the skip to return.
     * @return The requested skip.
     */
    @Operation(
        summary = "Get onboarding skip by ID",
        description = "Returns one onboarding skip request by its identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/skips/{skipId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getSkipById(
        @Parameter(description = "UUID of the onboarding skip")
        @PathVariable skipId: UUID,
    ): GetOnboardingSkipResponse {
        return onboardingSkipService.getSkipById(skipId)
    }

    /**
     * Accepts one pending onboarding skip request.
     *
     * Accepting a skip resolves the request and marks the parent step as skipped.
     *
     * @param skipId Identifier of the skip to accept.
     * @param request Review payload containing the admin comment.
     * @return The resolved skip review response.
     */
    @Operation(
        summary = "Accept onboarding skip",
        description = "Accepts a pending onboarding skip request and marks the parent step as skipped.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip accepted successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be accepted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to review onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/skips/{skipId}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    fun acceptSkipById(
        @Parameter(description = "UUID of the onboarding skip to accept")
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: ReviewOnboardingSkipRequest,
    ): ReviewOnboardingSkipResponse {
        return onboardingSkipService.acceptSkipById(skipId, request)
    }

    /**
     * Denies one pending onboarding skip request.
     *
     * Denying a skip resolves the request and leaves the parent step waiting.
     *
     * @param skipId Identifier of the skip to deny.
     * @param request Review payload containing the admin comment.
     * @return The resolved skip review response.
     */
    @Operation(
        summary = "Deny onboarding skip",
        description = "Denies a pending onboarding skip request and leaves the parent step waiting.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip denied successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be denied"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to review onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/skips/{skipId}/deny")
    @PreAuthorize("hasRole('ADMIN')")
    fun denySkipById(
        @Parameter(description = "UUID of the onboarding skip to deny")
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: ReviewOnboardingSkipRequest,
    ): ReviewOnboardingSkipResponse {
        return onboardingSkipService.denySkipById(skipId, request)
    }

    /**
     * Deletes one onboarding skip request by ID.
     *
     * Only pending skip requests can be deleted.
     *
     * @param skipId Identifier of the skip to delete.
     */
    @Operation(
        summary = "Delete onboarding skip",
        description = "Deletes one onboarding skip request by its identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Skip deleted successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/skips/{skipId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteSkipById(
        @Parameter(description = "UUID of the onboarding skip to delete")
        @PathVariable skipId: UUID,
    ) {
        onboardingSkipService.deleteSkipById(skipId)
    }
}
