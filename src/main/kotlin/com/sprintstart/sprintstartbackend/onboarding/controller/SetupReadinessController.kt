package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.setup.SetupReadinessResponse
import com.sprintstart.sprintstartbackend.onboarding.service.SetupReadinessService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * "Is this project ready to onboard someone?" -- for the people responsible for setting it up.
 *
 * One read that composes the four setup stages the onboarding module owns (skill map, baseline,
 * starter tasks, buddies) so a PM sees them as one pipeline with one verdict instead of four
 * disconnected pages. Read-only and derived on request; no state of its own.
 */
@RestController
@RequestMapping("/api/v1/onboarding/setup")
@Tag(
    name = "Onboarding - Setup",
    description = "Whether a project is ready to onboard someone, as a ladder of setup stages",
)
class SetupReadinessController(
    private val setupReadinessService: SetupReadinessService,
) {
    @Operation(
        summary = "Project onboarding readiness",
        description = "The state of each setup stage for one project -- an approved skill map, a " +
            "chosen baseline, a stocked pool of starter tasks, and a buddy for every hire -- plus " +
            "whether they are all done. Corpus health is not included: it lives with data " +
            "ingestion and the client composes it onto the top of the ladder.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Readiness returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('PM', 'HR', 'ADMIN')")
    fun getStatus(@RequestParam projectId: UUID): SetupReadinessResponse =
        setupReadinessService.getReadiness(projectId)
}
