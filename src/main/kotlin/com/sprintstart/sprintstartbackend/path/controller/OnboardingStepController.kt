package com.sprintstart.sprintstartbackend.path.controller

import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.response.path.TeamOverviewUserDto
import com.sprintstart.sprintstartbackend.path.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.service.OnboardingPathService
import com.sprintstart.sprintstartbackend.path.service.OnboardingStepService
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes the authenticated user's own onboarding step endpoints; staff endpoints for any phase live in
 * [OnboardingStepAdminController].
 *
 * Steps sit in the middle of the onboarding tree. Nesting depth for the onboarding
 * hierarchy is `0 = path`, `1 = phases`, `2 = steps`, and `3 = tasks/resources`.
 * Step endpoints therefore operate on depth-2 objects and may expose direct children
 * at depth 3.
 *
 * Steps are ordered within their parent phase by a numeric position. Insertions, updates,
 * and deletions automatically shift sibling steps to maintain a contiguous, gap-free ordering.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Steps",
    description = "Create, retrieve, update, " +
        "and delete onboarding steps at hierarchy depth 2",
)
class OnboardingStepController(
    val onboardingStepService: OnboardingStepService,
    val onboardingPathService: OnboardingPathService,
) {
    /**
     * Returns all steps for one phase in the authenticated user's onboarding path.
     *
     * The returned objects are at depth 2. Each step may have direct children at depth 3:
     * tasks and resources.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the parent phase at depth 1.
     * @return All steps for the phase.
     */
    @Operation(
        summary = "Get current user's onboarding steps by phase",
        description = "Returns onboarding steps at hierarchy depth 2 for the authenticated user. " +
            "Each returned step may have direct children at depth 3: tasks and resources.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Steps returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding steps"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/team-overview")
    @PreAuthorize("hasRole('USER')")
    fun getMyTeamOverview(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): TeamOverviewUserDto {
        return onboardingPathService.getTeamOverviewForMe(jwt.subject)
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/phases/{phaseId}/steps")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingStepsForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the parent onboarding phase")
        @PathVariable phaseId: UUID,
    ): List<GetOnboardingStepsResponse> {
        return onboardingStepService.getOnboardingStepsForMe(jwt.subject, phaseId)
    }

    /**
     * Creates a step in one phase of the authenticated user's onboarding path.
     *
     * The created object is at depth 2 below the path and phase.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the parent phase at depth 1.
     * @param request Step creation payload.
     * @return The created step.
     */
    @Operation(
        summary = "Create current user's onboarding step",
        description = "Creates an onboarding step at hierarchy depth 2 " +
            "for the authenticated user under the specified phase at depth 1.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Step created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid step position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding steps"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding phase found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/phases/{phaseId}/steps")
    @PreAuthorize("hasRole('USER')")
    fun createOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the parent onboarding phase")
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        return onboardingStepService.createOnboardingStepForMe(jwt.subject, phaseId, request)
    }

    /**
     * Returns one step from the authenticated user's onboarding path.
     *
     * The returned object is at depth 2 and may include direct children at depth 3:
     * tasks and resources.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to return.
     * @return The requested step.
     */
    @Operation(
        summary = "Get current user's onboarding step",
        description = "Returns one onboarding step at hierarchy depth 2 for the authenticated user. " +
            "The maximum child depth below the step is depth 3 for tasks and resources.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding step"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("me/steps/{stepId}")
    @PreAuthorize("hasRole('USER')")
    fun getOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step")
        @PathVariable stepId: UUID,
    ): GetOnboardingStepResponse {
        return onboardingStepService.getOnboardingStepForMe(jwt.subject, stepId)
    }

    /**
     * Updates one step in the authenticated user's onboarding path.
     *
     * The target object is at depth 2. This endpoint only updates step metadata.
     * Position changes behave the same as the admin variant. Status changes are
     * handled by separate endpoints.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to update.
     * @param request Step update payload.
     * @return The updated step.
     */
    @Operation(
        summary = "Update current user's onboarding step",
        description = "Updates an onboarding step at hierarchy depth 2 for the authenticated user. " +
            "This endpoint updates step metadata only. Changing the position reorders sibling steps. " +
            "Status changes are handled by separate endpoints.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid step position"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding step"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/steps/{stepId}")
    @PreAuthorize("hasRole('USER')")
    fun updateOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step to update")
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateOnboardingStepRequest,
    ): UpdateOnboardingStepResponse {
        return onboardingStepService.updateOnboardingStepForMe(jwt.subject, stepId, request)
    }

    /**
     * Marks one step in the authenticated user's onboarding path as completed.
     *
     * The target object is at depth 2. Only steps in [StepStatus.WAITING] or
     * [StepStatus.IN_PROGRESS] can be completed through this endpoint. Completing a step
     * records its completion timestamp and returns the updated step response.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to complete.
     * @return The updated step after it has been marked as completed.
     */
    @Operation(
        summary = "Complete current user's onboarding step",
        description = "Marks an onboarding step at hierarchy depth 2 as completed for the authenticated user. " +
            "Only steps in WAITING or IN_PROGRESS status can be completed. " +
            "Completing a step records its completion timestamp and returns the updated step.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step completed successfully"),
            ApiResponse(responseCode = "400", description = "Step cannot be completed in its current status"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/steps/{stepId}/complete")
    @PreAuthorize("hasRole('USER')")
    fun completeOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step to complete")
        @PathVariable stepId: UUID,
    ): UpdateOnboardingStepResponse {
        return onboardingStepService.completeOnboardingStepForMe(jwt.subject, stepId)
    }

    /**
     * Marks one step in the authenticated user's onboarding path as in progress.
     *
     * The target object is at depth 2. Starting a step records its start timestamp the
     * first time it is started so the time spent on it can be tracked. Steps that are
     * already finished or skipped cannot be started.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to start.
     * @return The updated step after it has been marked as in progress.
     */
    @Operation(
        summary = "Start current user's onboarding step",
        description = "Marks an onboarding step at hierarchy depth 2 as in progress for the authenticated user. " +
            "The start timestamp is recorded the first time the step is started. " +
            "Finished or skipped steps cannot be started.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step started successfully"),
            ApiResponse(responseCode = "400", description = "Step cannot be started in its current status"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/steps/{stepId}/start")
    @PreAuthorize("hasRole('USER')")
    fun startOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step to start")
        @PathVariable stepId: UUID,
    ): UpdateOnboardingStepResponse {
        return onboardingStepService.startOnboardingStepForMe(jwt.subject, stepId)
    }

    /**
     * Deletes one step from the authenticated user's onboarding path.
     *
     * The deleted object is at depth 2. Any descendants below it are at depth 3:
     * tasks and resources.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param stepId Identifier of the step to delete.
     */
    @Operation(
        summary = "Delete current user's onboarding step",
        description = "Deletes an onboarding step at hierarchy depth 2 for the authenticated user. " +
            "Any descendants below the step are limited to depth 3: tasks and resources.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Step deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding step"),
            ApiResponse(
                responseCode = "404",
                description = "No user or onboarding step found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/steps/{stepId}")
    @PreAuthorize("hasRole('USER')")
    fun deleteOnboardingStepForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the onboarding step to delete")
        @PathVariable stepId: UUID,
    ) {
        onboardingStepService.deleteOnboardingStepForMe(jwt.subject, stepId)
    }
}
