package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyProposalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Decides the changes the team-mode buddy stored as proposals.
 *
 * The client sends the proposal's id and nothing else: what runs is what was previewed, read from the
 * stored proposal, and the caller is re-authorised for its project on confirm.
 */
@RestController
@RequestMapping("/api/v1/onboarding/me/buddy/proposals")
@Tag(name = "Onboarding - Buddy", description = "A hire's persistent onboarding companion")
class BuddyProposalController(
    private val buddyProposalService: BuddyProposalService,
) {
    @Operation(
        summary = "Confirm a stored buddy proposal",
        description = "Runs a change the buddy proposed in team mode, exactly as it was previewed. Only the " +
            "manager it was proposed to can confirm it, only once, and only while they still manage the project " +
            "and the change still applies. Returns a single line to show; a refusal or handled failure is " +
            "`ok = false`, not an error.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Confirmation attempted; see the outcome"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No such proposal for the caller"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{proposalId}/confirm")
    @PreAuthorize("hasRole('USER')")
    suspend fun confirm(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "The id carried on the `action_proposal` stream event.")
        @PathVariable proposalId: UUID,
    ): BuddyActionResponse = buddyProposalService.confirm(jwt.subject, proposalId)

    @Operation(
        summary = "Dismiss a stored buddy proposal",
        description = "Declines a change the buddy proposed in team mode. Nothing changes but the proposal, " +
            "which can no longer be confirmed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Dismissal attempted; see the outcome"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No such proposal for the caller"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/{proposalId}/dismiss")
    @PreAuthorize("hasRole('USER')")
    fun dismiss(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "The id carried on the `action_proposal` stream event.")
        @PathVariable proposalId: UUID,
    ): BuddyActionResponse = buddyProposalService.dismiss(jwt.subject, proposalId)
}
