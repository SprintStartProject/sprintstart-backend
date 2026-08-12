package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.track.OnboardingTrackResponse
import com.sprintstart.sprintstartbackend.onboarding.service.TrackService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The onboarding tracks a PM can point a project role at.
 *
 * Read-only here: a track is authored in a migration rather than through an API, because adding one
 * means deciding what evidence it admits and what its work is called — a schema-level decision, not
 * a form field. What a PM *chooses* is which existing track a role belongs to, and that write lives
 * with the role, in the user module.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Tracks",
    description = "What onboarding means for each kind of role",
)
class OnboardingTrackController(
    private val trackService: TrackService,
) {
    @Operation(
        summary = "List onboarding tracks",
        description =
            "Every track a project role can be pointed at, with its vocabulary and the evidence " +
                "kinds it admits. A track admitting no evidence kinds cannot have its hires' work " +
                "observed by anything connected today.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Tracks returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @GetMapping("/tracks")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun listTracks(): List<OnboardingTrackResponse> {
        return trackService.listTracks().map { track ->
            OnboardingTrackResponse(
                key = track.key,
                label = track.label,
                contributionNoun = track.contributionNoun,
                contributionNounPlural = track.contributionNounPlural,
                contributionVerbPast = track.contributionVerbPast,
                evidenceKinds = track.evidenceKinds.sortedBy { it.name },
            )
        }
    }
}
