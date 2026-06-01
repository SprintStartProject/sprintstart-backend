package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathsResponse
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for managing onboarding paths.
 *
 * Exposes endpoints under `/api/v1/onboarding` for retrieving and deleting onboarding paths.
 * Paths are the top-level entity in the onboarding hierarchy and can be looked up either
 * by their own UUID or by the UUID of the user they belong to.
 *
 * Access rules:
 * - `GET /paths` — admin only (flat listing of all paths across all users)
 * - All other endpoints — accessible to users with the appropriate role
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - Paths", description = "Retrieve and delete onboarding paths")
class OnboardingPathController(
    private val onboardingService: OnboardingService,
) {
    /**
     * Returns all onboarding paths across all users.
     *
     * This is a flat listing intended for administrative overviews. No phases, steps,
     * tasks, or resources are included in the response.
     *
     * @return A flat list of all onboarding paths.
     */
    @Operation(
        summary = "Get all onboarding paths",
        description = "Returns a flat list of all onboarding paths across all users. No nested content is included.",
    )
    @ApiResponse(responseCode = "200", description = "Flat list of all onboarding paths")
    @GetMapping("/paths")
    fun getAllPaths(): List<GetOnboardingPathsResponse> {
        return onboardingService.getAllOnboardingPaths()
    }

    /**
     * Returns a single onboarding path by its ID, including its direct phases.
     *
     * The response includes one level of nesting: the path and its phases are returned,
     * but the phases do not include their steps.
     *
     * @param pathId The UUID of the onboarding path.
     * @return The onboarding path with its phases.
     */
    @Operation(
        summary = "Get onboarding path by ID",
        description = "Returns a single onboarding path by its UUID. Includes one level of nesting: " +
                " the path's direct phases are included, but steps within those phases are not.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path with direct phases"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @GetMapping("/paths/{pathId}")
    fun getPath(
        @Parameter(description = "UUID of the onboarding path") @PathVariable pathId: UUID,
    ): GetOnboardingPathResponse {
        return onboardingService.getOnboardingPath(pathId)
    }

    /**
     * Returns the onboarding path for a specific user with deep nesting.
     *
     * This is the primary endpoint for rendering a user's full onboarding view. Unlike
     * other get-by-ID endpoints that return only one level of nesting, this endpoint
     * returns the full Path → Phases → Steps structure. Tasks and resources within steps
     * are not included. Both the user and their path must exist, otherwise a 404 is returned.
     *
     * @param userId The UUID of the user.
     * @return The user's onboarding path with nested phases and steps.
     */
    @Operation(
        summary = "Get onboarding path for a user",
        description = "Returns the onboarding path for the specified user with deep nesting: Path → Phases → Steps. " +
            "This is the primary endpoint for rendering a user's onboarding view. " +
            "Tasks and resources within steps are not included. " +
            "Returns 404 if the user does not exist or has no onboarding path assigned.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path with nested phases and steps"),
        ApiResponse(responseCode = "404", description = "No user or onboarding path found for the given user ID"),
    )
    @GetMapping("/{userId}/path")
    fun getPathForUser(
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
    ): GetOnboardingPathForUserResponse {
        return onboardingService.getOnboardingPathByUserId(userId)
    }

    /**
     * Deletes an onboarding path by its ID.
     *
     * This is a hard delete. All associated phases, steps, tasks, and resources are
     * removed via cascade. This operation is not reversible.
     *
     * @param pathId The UUID of the onboarding path to delete.
     */
    @Operation(
        summary = "Delete onboarding path by ID",
        description = "Permanently deletes the onboarding path with the given UUID. " +
            "All associated phases, steps, tasks, and resources are removed via cascade." +
            " This operation is not reversible.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path deleted"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/paths/{pathId}")
    fun deletePath(
        @Parameter(description = "UUID of the onboarding path to delete") @PathVariable pathId: UUID,
    ) {
        onboardingService.deleteOnboardingPathById(pathId)
    }

    /**
     * Deletes the onboarding path associated with a specific user.
     *
     * Behaves identically to deleting by path ID, but accepts a user ID instead.
     * Useful when the path ID is unknown but the user ID is available. The user must
     * exist in the system, otherwise a 404 is returned before any deletion is attempted.
     *
     * @param userId The UUID of the user whose onboarding path should be deleted.
     */
    @Operation(
        summary = "Delete onboarding path by user ID",
        description = "Permanently deletes the onboarding path belonging to the specified user. " +
            "The user must exist in the system — a 404 is returned if the user is not found. " +
            "All associated phases, steps, tasks, and resources are removed via cascade.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding path deleted"),
        ApiResponse(responseCode = "404", description = "No user or onboarding path found for the given user ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{userId}/path")
    fun deletePathByUserId(
        @Parameter(description = "UUID of the user whose onboarding path should be deleted") @PathVariable userId: UUID,
    ) {
        onboardingService.deleteOnboardingPathByUserId(userId)
    }
}
