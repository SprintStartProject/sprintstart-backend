package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.ProjectAttentionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.ProjectOnboardingMetricsResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingMetricsService
import com.sprintstart.sprintstartbackend.onboarding.service.ProjectAttentionService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * How long onboarding is taking, for the people responsible for it.
 *
 * Read-only and derived on request: there is no metrics pipeline to fall behind, and the numbers
 * cover history from the day the underlying data existed rather than from the day this shipped.
 */
@RestController
@RequestMapping("/api/v1/onboarding/metrics")
@Tag(
    name = "Onboarding - Metrics",
    description = "Time-to-first-merged-PR, response latency and stalls",
)
class OnboardingMetricsController(
    private val onboardingMetricsService: OnboardingMetricsService,
    private val projectAttentionService: ProjectAttentionService,
    private val userApi: UserApi,
) {
    @Operation(
        summary = "My own onboarding timeline",
        description = "The self-serve counterpart of the PM-facing per-hire timeline: a hire reads " +
            "their own moments and gaps. Carries `longestOpenWaitHours` — how long their pull " +
            "request has been waiting on anyone — which is the number that tells a stuck newcomer " +
            "the delay is not their fault. Project-scoped, because onboarding is per-project.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Timeline returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun getMyTimeline(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): HireTimelineResponse {
        val userId = userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: ${jwt.subject}")
        }
        return onboardingMetricsService.getHireTimeline(userId, projectId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "You are not a member of project $projectId",
            )
    }

    @Operation(
        summary = "Onboarding metrics for a project",
        description = "Per-hire timelines plus the aggregates: median time to a first merged pull request, " +
            "median and 90th-percentile wait for a first response, stalls, and pull requests waiting on anyone.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Metrics returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/projects/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getProjectMetrics(@PathVariable projectId: UUID): ProjectOnboardingMetricsResponse =
        onboardingMetricsService.getProjectMetrics(projectId)

    @Operation(
        summary = "One hire's onboarding timeline",
        description = "Joined, claimed a task, opened a pull request, got a response, merged — with the gaps.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Timeline returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "That user is not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/projects/{projectId}/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getHireTimeline(
        @PathVariable projectId: UUID,
        @PathVariable userId: UUID,
    ): HireTimelineResponse =
        onboardingMetricsService.getHireTimeline(userId, projectId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $userId is not a member of project $projectId",
            )

    @Operation(
        summary = "Who needs a human today",
        description = "Blocked before drifting, longest wait first: pull requests kept waiting on a " +
            "response (somebody else's move) and stalls the metrics found. Escalation to a person " +
            "runs through flag-to-PM, not an assigned buddy.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Attention list returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/projects/{projectId}/attention")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getAttention(@PathVariable projectId: UUID): ProjectAttentionResponse =
        projectAttentionService.getAttention(projectId)
}
