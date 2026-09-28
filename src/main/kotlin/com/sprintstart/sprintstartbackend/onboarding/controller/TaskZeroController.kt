package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.starterwork.SetTaskZeroEligibilityRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.MyTaskZeroResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.onboarding.service.TaskZeroService
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
 * Task 0: the trivial first task a hire is auto-assigned once their environment is ready.
 *
 * A PM flags which live starter-work tasks are suitable; a hire reads (and, on first read, is
 * assigned) their own, and may undo it. Assignment is deliberately a hire-side read side effect
 * rather than a background job, so it covers derived readiness too — a PM viewing the metrics never
 * assigns anything.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Task 0",
    description = "The trivial first task that proves the branch → PR → review → merge loop",
)
class TaskZeroController(
    private val taskZeroService: TaskZeroService,
    private val userApi: UserApi,
) {
    @Operation(
        summary = "Flag an approved task as suitable for Task 0",
        description = "A PM's decision that this live starter-work task is small and safe enough " +
            "to be a hire's first task. Only valid on an approved proposal.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Eligibility updated"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No such starter-work task"),
            ApiResponse(responseCode = "409", description = "The task is not approved"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/starter-work/{proposalId}/task-zero")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun setEligibility(
        @PathVariable proposalId: UUID,
        @RequestBody request: SetTaskZeroEligibilityRequest,
    ): StarterWorkTaskProposalResponse = taskZeroService.setEligibility(proposalId, request.eligible)

    @Operation(
        summary = "My Task 0 on a project",
        description = "Assigns one automatically if my environment is ready and none is assigned yet. " +
            "Not-ready, and ready-but-nothing-eligible, are ordinary states rather than errors.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Task 0 state returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/task-zero")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun getMyTaskZero(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): MyTaskZeroResponse = taskZeroService.getForHire(resolveUserId(jwt), projectId)

    @Operation(
        summary = "Undo my Task 0 assignment",
        description = "Frees the task for someone else. A no-op when nothing is assigned; " +
            "earns nothing, so un-earns nothing.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Assignment removed (or there was none)"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/task-zero")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun unassignMyTaskZero(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ) = taskZeroService.unassign(resolveUserId(jwt), projectId)

    private fun resolveUserId(jwt: Jwt): UUID =
        userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: ${jwt.subject}")
        }
}
