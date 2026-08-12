package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.history.GithubHistoryPriorResponse
import com.sprintstart.sprintstartbackend.onboarding.service.GithubHistoryPriorService
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Consent for, and visibility into, using a user's existing repository work to calibrate their
 * skill assessment.
 *
 * Every endpoint is self-serve only: a user grants, inspects and withdraws their own consent, and
 * no PM-facing variant exists. The read returns exactly what is stored about them, so opting in is
 * never a black box.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding — GitHub history")
class GithubHistoryController(
    private val githubHistoryPriorService: GithubHistoryPriorService,
    private val userApi: UserApi,
) {
    /**
     * Opts in and immediately derives the prior, so the response already shows what was inferred.
     *
     * @return The derived signal; empty when the user has authored nothing in their projects'
     * connected repositories (or has not declared a GitHub username yet).
     */
    @Operation(
        summary = "Consent to using your repository work for assessment calibration",
        description = "Grants consent and derives the prior from artifacts already ingested for " +
            "the user's projects. No GitHub call is made and no activity outside those " +
            "repositories is read.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Consent recorded and prior derived"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated subject"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/github-history/consent")
    @PreAuthorize("hasRole('USER')")
    fun grantConsent(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GithubHistoryPriorResponse =
        GithubHistoryPriorResponse.from(githubHistoryPriorService.grantConsent(resolveUserId(jwt)))

    /**
     * Withdraws consent and deletes the derived prior.
     *
     * A placement already made from it is not reverted -- the user earned it.
     */
    @Operation(
        summary = "Withdraw consent and delete the derived prior",
        description = "Clears consent and deletes the stored signal. Any assessment placement " +
            "already made from it is kept.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Consent withdrawn and prior deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated subject"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/github-history/consent")
    @PreAuthorize("hasRole('USER')")
    fun revokeConsent(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        githubHistoryPriorService.revokeConsent(resolveUserId(jwt))
    }

    /**
     * Returns everything inferred about the user from their repository work.
     *
     * @return The stored prior, or `consented = false` with no signal when they have not opted in.
     */
    @Operation(
        summary = "See what was inferred from your repository work",
        description = "Returns the full derived signal held about the authenticated user, or an " +
            "un-consented placeholder when they have not opted in.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Prior returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated subject"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/github-history")
    @PreAuthorize("hasRole('USER')")
    fun getPrior(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GithubHistoryPriorResponse {
        val prior = githubHistoryPriorService.getPrior(resolveUserId(jwt))
        return prior?.let { GithubHistoryPriorResponse.from(it) } ?: GithubHistoryPriorResponse.notConsented()
    }

    private fun resolveUserId(jwt: Jwt) =
        userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found for the authenticated subject")
        }
}
