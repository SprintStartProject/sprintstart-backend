package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.entity.Attestation
import com.sprintstart.sprintstartbackend.onboarding.model.request.RequestAttestationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.SendBackAttestationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.attestation.AttestationResponse
import com.sprintstart.sprintstartbackend.onboarding.service.AttestationService
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Asking a colleague to confirm your work, and answering when you are asked.
 *
 * Every route resolves the caller from their token: a hire can only request against themselves, and
 * only the named attester can answer. That is what keeps attested evidence worth anything — the
 * whole point is that somebody *other than the hire* said it.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Attestations",
    description = "Work confirmed by a named colleague, for roles nothing observes",
)
class AttestationController(
    private val attestationService: AttestationService,
    private val projectMembershipApi: ProjectMembershipApi,
    private val userApi: UserApi,
) {
    @Operation(
        summary = "Ask somebody to confirm your work",
        description =
            "Files a request against the caller. The attester must be another member of the same " +
                "project: work confirmed by the person who did it is not evidence.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Request filed"),
            ApiResponse(
                responseCode = "400",
                description = "Blank title, self-attestation, or an attester who is not on the project",
            ),
            ApiResponse(responseCode = "404", description = "Caller is not a member of the project"),
        ],
    )
    @PostMapping("/me/attestations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun request(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: RequestAttestationRequest,
    ): AttestationResponse {
        val hireId = resolveCaller(jwt)
        return attestationService
            .request(hireId, request.projectId, request.title, request.evidenceUrl, request.attesterId)
            .toResponse()
    }

    @Operation(summary = "Your attestation requests on a project")
    @GetMapping("/me/attestations")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun mine(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): List<AttestationResponse> {
        return attestationService.forHire(resolveCaller(jwt), projectId).map { it.toResponse() }
    }

    @Operation(
        summary = "What is waiting on you to confirm",
        description = "Everything somebody has asked the caller to confirm, longest-waiting first.",
    )
    @GetMapping("/attestations/pending")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun pending(@AuthenticationPrincipal jwt: Jwt): List<AttestationResponse> {
        return attestationService.pendingFor(resolveCaller(jwt)).map { it.toResponse() }
    }

    @Operation(summary = "Confirm the work happened and met the bar")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Confirmed"),
            ApiResponse(responseCode = "403", description = "Caller is not the person who was asked"),
            ApiResponse(responseCode = "409", description = "No longer waiting on an answer"),
        ],
    )
    @PostMapping("/attestations/{id}/accept")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun accept(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): AttestationResponse {
        return attestationService.accept(id, resolveCaller(jwt)).toResponse()
    }

    @Operation(
        summary = "Send the work back with what needs to change",
        description = "Counts as rework, exactly as a pull request sent back for changes does.",
    )
    @PostMapping("/attestations/{id}/send-back")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun sendBack(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody request: SendBackAttestationRequest,
    ): AttestationResponse {
        return attestationService.sendBack(id, resolveCaller(jwt), request.reason).toResponse()
    }

    @Operation(summary = "Withdraw a request you no longer want answered")
    @PostMapping("/me/attestations/{id}/withdraw")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun withdraw(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
    ): AttestationResponse {
        return attestationService.withdraw(id, resolveCaller(jwt)).toResponse()
    }

    /**
     * Names both parties on the way out.
     *
     * A bare user id is unusable in a queue somebody works through — "confirm this for 8f21…" is not
     * a question anyone can answer. Resolved per project so it uses the same display name the rest
     * of the onboarding surfaces do; an unknown id degrades to null rather than failing the read.
     */
    private fun Attestation.toResponse(): AttestationResponse {
        val names = projectMembershipApi
            .getProjectMembers(projectId)
            .associate { it.userId to it.displayName }
        return AttestationResponse(
            id = id,
            hireId = hireId,
            hireName = names[hireId],
            projectId = projectId,
            title = title,
            evidenceUrl = evidenceUrl,
            attesterId = attesterId,
            attesterName = names[attesterId],
            state = state,
            requestedAt = requestedAt,
            firstResponseAt = firstResponseAt,
            acceptedAt = acceptedAt,
            returnedCount = returnedCount,
            returnReason = returnReason,
        )
    }

    private fun resolveCaller(jwt: Jwt): UUID =
        userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
}
