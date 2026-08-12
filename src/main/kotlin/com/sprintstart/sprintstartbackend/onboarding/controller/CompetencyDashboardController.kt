package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.CompetencyAggregateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.UserCompetencyStateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.dashboard.UserCompetencySummaryResponse
import com.sprintstart.sprintstartbackend.onboarding.service.CompetencyDashboardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * PM-facing team-wide competency ledger signal -- aggregate and per-user, sourced from
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState].
 */
@RestController
@RequestMapping("/api/v1/onboarding/dashboard")
@Tag(
    name = "Onboarding - Competency Dashboard",
    description = "Team-wide competency ledger signal for PMs, aggregate and per-user",
)
class CompetencyDashboardController(
    private val competencyDashboardService: CompetencyDashboardService,
) {
    /**
     * Returns, for every live competency, how many users hold it at each level and by which
     * ledger source.
     */
    @Operation(
        summary = "Get team-wide competency aggregate",
        description = "Returns, for every live competency, the level and source distribution across all users",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Aggregate signal returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/competencies")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getCompetencyAggregate(): List<CompetencyAggregateResponse> =
        competencyDashboardService.getCompetencyAggregate()

    /**
     * Returns a paginated, per-user breakdown of each user's full competency ledger.
     *
     * @param search Optional search string to filter users.
     * @param roleIds Optional list of project role UUIDs to filter users.
     * @param projectIds Optional list of project UUIDs to filter users.
     * @param pageable Pagination parameters.
     */
    @Operation(
        summary = "Get per-user competency ledger breakdown",
        description = "Returns a paginated list of users with their full competency ledger. " +
            "Supports filtering by search query, project roles, and projects.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Per-user breakdown returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getUserCompetencySummaries(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) roleIds: List<UUID>?,
        @RequestParam(required = false) projectIds: List<UUID>?,
        pageable: Pageable,
    ): Page<UserCompetencySummaryResponse> =
        competencyDashboardService.getUserCompetencySummaries(search, roleIds, projectIds, pageable)

    /**
     * Returns one user's full competency ledger, labeled.
     *
     * @param userId Identifier of the user whose ledger should be returned.
     */
    @Operation(
        summary = "Get one user's competency ledger",
        description = "Returns the user's full competency ledger with labels -- the per-member " +
            "view for the PM member detail page.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ledger returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found with this id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getUserCompetencyStates(
        @Parameter(description = "UUID of the user whose ledger should be returned")
        @PathVariable userId: UUID,
    ): List<UserCompetencyStateResponse> = competencyDashboardService.getUserCompetencyStates(userId)
}
