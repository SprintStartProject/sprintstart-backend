package com.sprintstart.sprintstartbackend.starterwork.controller

import com.sprintstart.sprintstartbackend.starterwork.model.request.starterwork.ClaimGoalRequest
import com.sprintstart.sprintstartbackend.starterwork.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.starterwork.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.starterwork.service.StarterWorkTaskProposalService
import com.sprintstart.sprintstartbackend.starterwork.service.UserGoalService
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes the hire's own side of the starter-work pool: the ranked matches for a project, and claiming or
 * clearing the goal task. Reviewing the pool lives in [StarterWorkController].
 */
@RestController
@RequestMapping("/api/v1/onboarding/starter-work")
@Tag(
    name = "Onboarding - Starter Work",
    description = "Review AI-mined starter-work task proposals and rank them by hire fit",
)
class MyStarterWorkController(
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val userGoalService: UserGoalService,
) {
    /**
     * Ranks the live starter-work pool by fit against the authenticated user's competencies.
     *
     * @param jwt Authenticated JWT that identifies the current user.
     */
    @Operation(
        summary = "Get current user's starter-work matches",
        description = "Ranks the live starter-work pool by fit for the authenticated user on a " +
            "project. Deterministic and local — no AI call: competency overlap is one input among " +
            "task type, prior involvement and label familiarity, and a repository that answers " +
            "pull requests slowly demotes its tasks without ever hiding them. Every task carries " +
            "the reasons it was suggested, because a suggestion nobody can interrogate is an " +
            "instruction.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ranked matches returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated principal"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/matches")
    @PreAuthorize("hasRole('USER')")
    fun getMatchesForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): List<RankedStarterWorkTaskResponse> = starterWorkTaskProposalService.matchForUser(jwt.subject, projectId)

    /**
     * Claims a live starter-work task as the authenticated hire's goal for a project.
     *
     * The hire chooses their own destination from the ranked matches above; a PM still controls
     * which tasks exist by approving proposals, so this is a choice within a curated set.
     */
    @Operation(
        summary = "Claim a starter-work task as my goal",
        description = "Sets the contribution the authenticated user's path for this project aims at, " +
            "replacing any goal they had claimed there before",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Goal claimed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(
                responseCode = "404",
                description = "No such task, no user for the principal, or the task has no contribution node",
            ),
            ApiResponse(responseCode = "409", description = "The task has not been approved"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/goal")
    @PreAuthorize("hasRole('USER')")
    fun claimGoalForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
        @RequestBody request: ClaimGoalRequest,
    ): GoalView = userGoalService.claimForMe(jwt.subject, projectId, request.taskId)

    /**
     * Drops the authenticated hire's goal for a project; their path falls back to the baseline.
     */
    @Operation(
        summary = "Drop my goal",
        description = "Clears the contribution this user's path for the given project aims at",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Goal cleared"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated principal"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/goal")
    @PreAuthorize("hasRole('USER')")
    fun clearGoalForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ) = userGoalService.clearForMe(jwt.subject, projectId)
}
