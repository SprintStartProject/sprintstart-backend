package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPathService
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPersonalizationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes onboarding path endpoints.
 *
 * The onboarding hierarchy starts at the path level. Nesting depth for the onboarding
 * tree is `0 = path`, `1 = phases`, `2 = steps`, and `3 = tasks/resources`. Path
 * endpoints therefore address the root object and may return nested descendants.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - Paths", description = "Retrieve and delete onboarding paths at hierarchy depth 0")
class OnboardingPathController(
    private val onboardingPathService: OnboardingPathService,
    private val onboardingPersonalizationService: OnboardingPersonalizationService,
) {
//  ========================== Endpoints for users (/me/...) ==========================

    /**
     * Returns the authenticated user's onboarding path.
     *
     * This endpoint returns the root of the onboarding tree at depth 0. The frontend
     * can treat the returned path as the top-level container for descendants at depths
     * 1 through 3: phases, steps, and then tasks/resources.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The authenticated user's onboarding path.
     */
    @Operation(
        summary = "Get current user's onboarding path",
        description = "Returns the onboarding path at hierarchy depth 0 for the authenticated user. " +
            "This is the root container above phases (depth 1), steps (depth 2), and tasks/resources (depth 3).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Onboarding path returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding path"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding " +
                    "path found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/path")
    @PreAuthorize("hasRole('USER')")
    fun getPathForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GetOnboardingPathForUserResponse {
        return onboardingPathService.getOnboardingPathForMe(jwt.subject)
    }

    /**
     * Deletes the authenticated user's onboarding path.
     *
     * This removes the hierarchy root at depth 0. Any nested descendants below that
     * root are deleted according to the persistence rules of the underlying model.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     */
    @Operation(
        summary = "Delete current user's onboarding path",
        description = "Deletes the onboarding path at hierarchy depth 0 for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Onboarding path deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding path"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/path")
    @PreAuthorize("hasRole('USER')")
    fun deletePathForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        onboardingPathService.deleteOnboardingPathForMe(jwt.subject)
    }

    /**
     * Generates an AI-personalized onboarding path for the authenticated user.
     *
     * The user's working area and experience are read from their profile.
     * Any existing path is replaced. The response is an SSE stream with
     * `stage`, `path`, `done`, and `error` events.
     */
    @Operation(
        summary = "Personalize onboarding path via AI",
        description = "Triggers AI generation of a personalized onboarding path " +
            "for the authenticated user. Returns an SSE stream with progress events.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "SSE stream of personalization events"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/path/personalize", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasRole('USER')")
    fun personalizePath(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): Flow<OnboardingSseEvent> {
        return onboardingPersonalizationService.personalize(jwt.subject)
    }

//  ========================== Endpoints for admins ==========================

    /**
     * Returns the onboarding path for a specific user.
     *
     * This endpoint returns the root object at depth 0 for the selected user. The
     * returned path sits above phases (depth 1), steps (depth 2), and tasks/resources
     * (depth 3).
     *
     * @param userId Identifier of the user whose path should be returned.
     * @return The selected user's onboarding path.
     */
    @Operation(
        summary = "Get onboarding path by user ID",
        description = "Returns the onboarding path at hierarchy depth 0 for the specified user. " +
            "The path is the root above phases (depth 1), steps (depth 2), and tasks/resources (depth 3).",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Onboarding path returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding path"),
            ApiResponse(responseCode = "404", description = "No user or onboarding path found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/path")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingPathForUserId(
        @Parameter(
            description = "UUID of the user whose onboarding path should be returned",
        ) @PathVariable userId: UUID,
    ): GetOnboardingPathResponse {
        return onboardingPathService.getOnboardingPathByUserId(userId)
    }

    /**
     * Deletes the onboarding path for a specific user.
     *
     * This removes the root object at depth 0 for the selected user.
     *
     * @param userId Identifier of the user whose path should be deleted.
     */
    @Operation(
        summary = "Delete onboarding path by user ID",
        description = "Deletes the onboarding path at hierarchy depth 0 for the specified user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Onboarding path deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding path"),
            ApiResponse(responseCode = "404", description = "No user found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/users/{userId}/path")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deletePathByUserId(
        @Parameter(description = "UUID of the user whose onboarding path should be deleted") @PathVariable userId: UUID,
    ) {
        onboardingPathService.deleteOnboardingPathByUserId(userId)
    }
}
