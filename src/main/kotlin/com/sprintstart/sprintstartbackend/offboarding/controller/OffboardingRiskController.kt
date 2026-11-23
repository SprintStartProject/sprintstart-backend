package com.sprintstart.sprintstartbackend.offboarding.controller

import com.sprintstart.sprintstartbackend.offboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskParameters
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.service.OffboardingRiskService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PM-only view of what a project loses when people leave.
 *
 * The risk map names the people who stay as knowers of a file, so it is limited to the project's
 * managers and admins.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/offboarding")
@Tag(name = "Offboarding", description = "Knowledge at risk when people leave a project")
class OffboardingRiskController(
    private val offboardingRiskService: OffboardingRiskService,
) {
    /**
     * Simulates a departure and ranks the files that lose the people who know them.
     */
    @Operation(
        summary = "Rank the files at risk when people leave",
        description = "Removes the named people from the recorded history and lists the files left with no " +
            "author (ORPHANED) or one author (HOARDED), most urgent first. Deterministic: the same people, " +
            "date and settings give the same ranking. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Risk map returned"),
            ApiResponse(responseCode = "400", description = "Nobody named, a blank person or an invalid setting"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
        ],
    )
    @GetMapping("/risk")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun risk(
        @PathVariable projectId: ProjectId,
        parameters: RiskParameters,
    ): RiskMapResponse =
        offboardingRiskService.riskMap(projectId, parameters.toRequest()).toResponse(parameters.listLimit)
}
