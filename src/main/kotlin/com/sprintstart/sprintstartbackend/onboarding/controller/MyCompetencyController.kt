package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.service.MyCompetencyService
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Exposes the authenticated user's own competency ledger (`GET /me/competencies`).
 *
 * The self-serve counterpart of the PM-facing competency dashboard: a user can see everything they
 * have proven, with level and source. The ledger is global, so this endpoint is not project-scoped.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - My Competencies", description = "The authenticated user's own competency ledger")
class MyCompetencyController(
    private val myCompetencyService: MyCompetencyService,
) {
    /**
     * Returns the authenticated user's full durable competency ledger (level + source per
     * competency), labeled and typed from the competency catalog.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The user's ledger rows; empty when they have no ledger yet.
     */
    @Operation(
        summary = "Get current user's competency ledger",
        description = "Returns the authenticated user's full durable ledger: every competency they " +
            "hold with its level and source (ASSESSED/VERIFIED/DECLARED). The ledger is global and " +
            "not project-scoped.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ledger returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to read this ledger"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/competencies")
    @PreAuthorize("hasRole('USER')")
    fun getMyCompetencies(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): List<MyCompetencyResponse> = myCompetencyService.getMyCompetencies(jwt.subject)
}
