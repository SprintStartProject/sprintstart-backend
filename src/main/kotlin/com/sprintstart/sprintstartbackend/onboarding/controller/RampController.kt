package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.ramp.MyRampResponse
import com.sprintstart.sprintstartbackend.onboarding.service.RampService
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The ramp of real tasks: where a hire is, and whether onboarding has ended.
 *
 * Self-serve. The PM's view of the same facts lives on the slice-0 metrics readout, where the rest
 * of the cohort already is — a hire reaching autonomy belongs next to the other numbers about how
 * onboarding is going, not on a page of its own.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Ramp",
    description = "Where you are on the ramp of real tasks, and whether onboarding has ended",
)
class RampController(
    private val rampService: RampService,
    private val userApi: UserApi,
) {
    @Operation(
        summary = "My ramp on a project",
        description = "Which stage, the task I'm on, and what unlocked it — plus whether I've " +
            "reached autonomy, defined as a task completed with no buddy intervention and no " +
            "review rework. Reading this also credits any merged work not yet in the ledger, " +
            "which is idempotent. There is deliberately no completion percentage.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ramp state returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/ramp")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun getMyRamp(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): MyRampResponse = rampService.getForHire(resolveUserId(jwt), projectId)

    private fun resolveUserId(jwt: Jwt): UUID =
        userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: ${jwt.subject}")
        }
}
