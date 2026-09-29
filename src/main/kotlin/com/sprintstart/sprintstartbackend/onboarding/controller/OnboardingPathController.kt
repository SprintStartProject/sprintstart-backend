package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.graph.OnboardingGenerationStatusResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingGenerationRegistry
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPathService
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingPersonalizationService
import com.sprintstart.sprintstartbackend.onboarding.service.onboardingProfileInProject
import com.sprintstart.sprintstartbackend.user.external.UserApi
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
import org.springframework.web.server.ResponseStatusException
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
    private val userApi: UserApi,
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
     * Deleting a path and building a new one is a rebuild, which is the project manager's call (see
     * [ProjectOnboardingPathController.personalizePathForUser]). So only a PM or admin may, and only
     * for a path built from a project they manage ([OnboardingPathService.requireMayReplacePath]).
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     */
    @Operation(
        summary = "Delete current user's onboarding path",
        description = "Deletes the onboarding path at hierarchy depth 0 for the authenticated user. " +
            "PM and admin only, and only for a path built from a project the caller manages: " +
            "members cannot discard their own path.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Onboarding path deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role, or the path was built from a project the caller does not manage",
            ),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/path")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun deletePathForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        userApi.getUserIdByAuthId(jwt.subject).ifPresent { userId ->
            onboardingPathService.requireMayReplacePath(jwt.subject, userId, projectId = null)
        }
        onboardingPathService.deleteOnboardingPathForMe(jwt.subject)
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
    ): GetOnboardingPathForUserResponse {
        return onboardingPathService.getOnboardingPathByUserId(userId)
    }

    /**
     * Deletes the onboarding path for a specific user.
     *
     * This removes the root object at depth 0 for the selected user. The user then builds a new
     * one, so this is a rebuild too: only a PM or admin may, for a path built from a project they
     * manage ([OnboardingPathService.requireMayReplacePath]).
     *
     * @param userId Identifier of the user whose path should be deleted.
     * @param jwt Authenticated JWT of the caller.
     */
    @Operation(
        summary = "Delete onboarding path by user ID",
        description = "Deletes the onboarding path at hierarchy depth 0 for the specified user. " +
            "PM and admin only, and only for a path built from a project the caller manages.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Onboarding path deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role, or the path was built from a project the caller does not manage",
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/users/{userId}/path")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun deletePathByUserId(
        @Parameter(description = "UUID of the user whose onboarding path should be deleted")
        @PathVariable userId: UUID,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        onboardingPathService.requireMayReplacePath(jwt.subject, userId, projectId = null)
        onboardingPathService.deleteOnboardingPathByUserId(userId)
    }
}

/**
 * Project-scoped onboarding-path entry points.
 *
 * Mirror of the blueprint controllers: the project that seeds generation is a path variable, so
 * the client builds from the project it has selected rather than the service guessing from the
 * user's memberships.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding")
@Tag(
    name = "Onboarding - Paths (project-scoped)",
    description = "Create onboarding paths from a specific project's blueprint",
)
class ProjectOnboardingPathController(
    private val onboardingPersonalizationService: OnboardingPersonalizationService,
    private val onboardingGenerationRegistry: OnboardingGenerationRegistry,
    private val onboardingPathService: OnboardingPathService,
    private val userApi: UserApi,
) {
    /**
     * Reports whether a generation is running for the authenticated user, and whether [projectId]
     * has the active blueprint a new one needs.
     *
     * @param projectId The project the client would build from.
     * @return The generation status.
     */
    @Operation(
        summary = "Get onboarding path generation status",
        description = "Whether a path generation is running for the authenticated user (e.g. started " +
            "before a reload or in another tab), and whether the project has an active blueprint.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Generation status returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "User is not assigned to the project"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/path/generation")
    @PreAuthorize("hasRole('USER')")
    fun getGenerationStatus(
        @Parameter(description = "UUID of the project the client would build from")
        @PathVariable projectId: UUID,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): OnboardingGenerationStatusResponse {
        // The same gate as starting one: what a project has is only answered to its members.
        userApi.onboardingProfileInProject(jwt.subject, projectId)
        val run = onboardingGenerationRegistry.status(jwt.subject)
        val activeBlueprints = onboardingGenerationRegistry.activeBlueprintCount(projectId)
        return OnboardingGenerationStatusResponse(
            running = run != null,
            runningProjectId = run?.projectId,
            startedAt = run?.startedAt,
            activeBlueprintCount = activeBlueprints,
        )
    }

    /**
     * Creates an onboarding path from the selected project's active blueprint for the authenticated
     * user.
     *
     * Path generation is project-scoped: the user's path is copied from the active blueprint of
     * [projectId] — the project the frontend currently has selected — and never from a global
     * template. The service rejects a project the user is not assigned to. Any existing path is
     * replaced. A project must have exactly one active blueprint.
     *
     * A member builds their *first* path here. Rebuilding an existing one is the project manager's
     * call ([personalizePathForUser]), because it throws away the member's progress; the project's
     * manager and admins may still replace their own path from here
     * ([OnboardingPathService.requireMayReplacePath]).
     *
     * The generation runs detached from this request (see [OnboardingGenerationRegistry]): closing
     * the stream does not cancel it, and a request while one is running watches that one instead of
     * starting another.
     *
     * @param projectId The project whose active blueprint seeds the path.
     * @return A stream of progress events ending in the new path plus a `done` event.
     * @throws ResponseStatusException `403` when the user is not assigned to the project, or already
     * has a path they may not replace; `404` when the user does not exist.
     */
    @Operation(
        summary = "Create onboarding path from blueprint",
        description = "Copies the selected project's active blueprint into an " +
            "onboarding path for the authenticated user. The project is a path variable, " +
            "so the path matches the project the user has selected in the UI. Failures after the " +
            "stream has opened — e.g. a project without an active blueprint — are reported as an " +
            "`error` event inside the `200` stream, not as an HTTP status.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "SSE stream of personalization events"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role to create an onboarding path, " +
                    "the authenticated user is not assigned to the given project, " +
                    "or the user already has a path they may not replace",
            ),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/path/personalize", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasRole('USER')")
    fun personalizePath(
        @Parameter(description = "UUID of the project whose active blueprint seeds the path")
        @PathVariable projectId: UUID,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): Flow<OnboardingSseEvent> {
        // Watching a running generation stays open to its owner -- including one a PM started for
        // them. Only starting a new one over an existing path is the manager's call, checked under
        // the registry's start lock. An unknown user has no path; personalize answers them with 404.
        val userId = userApi.getUserIdByAuthId(jwt.subject).orElse(null)
        return onboardingGenerationRegistry.startOrAttach(
            authId = jwt.subject,
            projectId = projectId,
            beforeStart = {
                if (userId != null) onboardingPathService.requireMayReplacePath(jwt.subject, userId, projectId)
            },
        )
    }

    /**
     * Rebuilds a member's onboarding path from [projectId]'s active blueprint, on the project
     * manager's behalf.
     *
     * The same generation as [personalizePath], run for the member: it replaces their path (and
     * with it their progress), and the member's own onboarding page attaches to it like to one they
     * started. Closing this stream does not cancel it.
     *
     * The caller's rights reach only as far as [projectId], so everything is checked before a
     * running generation is attached to -- its events carry the member's whole path:
     *
     * - the member must be assigned to [projectId];
     * - a generation already running for them must be for [projectId] (else `409`);
     * - and a path they already have from another project may only be replaced by somebody who
     *   manages that project too ([OnboardingPathService.requireMayReplacePath]).
     *
     * @param projectId The project whose active blueprint seeds the path.
     * @param userId The member whose path is rebuilt.
     * @param jwt Authenticated JWT of the caller.
     * @return A stream of progress events ending in the new path plus a `done` event.
     * @throws ResponseStatusException `403` when the member is not assigned to the project or their
     * path is from a project the caller does not manage, `404` when the member does not exist, `409`
     * when a generation for another project is running for them.
     */
    @Operation(
        summary = "Rebuild a member's onboarding path",
        description = "Rebuilds the member's onboarding path from the project's active blueprint, " +
            "replacing the path they have. For the project's manager and admins. Failures after the " +
            "stream has opened are reported as an `error` event inside the `200` stream.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "SSE stream of personalization events"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(
                responseCode = "403",
                description = "Caller does not manage the project, the member is not assigned to it, " +
                    "or the member's path was built from a project the caller does not manage",
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given ID"),
            ApiResponse(
                responseCode = "409",
                description = "A generation for another project is already running for the member",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/users/{userId}/path/personalize", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    fun personalizePathForUser(
        @Parameter(description = "UUID of the project whose active blueprint seeds the path")
        @PathVariable projectId: UUID,
        @Parameter(description = "UUID of the member whose path is rebuilt")
        @PathVariable userId: UUID,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): Flow<OnboardingSseEvent> {
        val authId = userApi.getAuthIdByUserId(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        // Before any attach: `personalize` checks membership too, but only when a run starts.
        userApi.onboardingProfileInProject(authId, projectId)
        return onboardingGenerationRegistry.startOrAttach(
            authId = authId,
            projectId = projectId,
            sameProjectOnly = true,
            beforeStart = { onboardingPathService.requireMayReplacePath(jwt.subject, userId, projectId) },
        )
    }
}
