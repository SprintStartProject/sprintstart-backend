package com.sprintstart.sprintstartbackend.path.controller

import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.response.path.TeamOverviewUserDto
import com.sprintstart.sprintstartbackend.path.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.service.OnboardingPathService
import com.sprintstart.sprintstartbackend.path.service.OnboardingStepService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes onboarding step endpoints for staff (admin, PM, HR): steps of any phase, and the team overview.
 *
 * Nesting depth for the onboarding hierarchy is `0 = path`, `1 = phases`, `2 = steps`, and `3 = tasks/resources`.
 * Steps are ordered within their phase by a numeric position; changes shift siblings to keep it gap-free.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Steps",
    description = "Create, retrieve, update, " +
        "and delete onboarding steps at hierarchy depth 2",
)
class OnboardingStepAdminController(
    val onboardingStepService: OnboardingStepService,
    val onboardingPathService: OnboardingPathService,
) {
    /**
     * Returns all steps belonging to a specific phase, including their direct tasks and resources.
     *
     * The returned objects are at depth 2. This response includes one additional child
     * level beneath each step: tasks and resources at depth 3. No deeper nesting exists.
     * Steps are ordered by their position within the phase.
     *
     * @param phaseId The UUID of the onboarding phase.
     * @return An ordered list of steps with their direct tasks and resources.
     */
    @Operation(
        summary = "Get steps by phase ID",
        description = "Returns all onboarding steps belonging to the specified phase, ordered by position. " +
            "Each returned step is at hierarchy depth 2 and includes direct children at depth 3: " +
            "tasks and resources. No deeper nesting exists.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Ordered list of steps with direct tasks and resources"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding steps"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/phases/{phaseId}/steps")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingStepsForPhaseId(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
    ): List<GetOnboardingStepResponse> {
        return onboardingStepService.getOnboardingStepsByPhaseId(phaseId)
    }

    /**
     * Creates a new step within the specified phase at the given position.
     *
     * Steps are ordered within a phase by a numeric position. If the requested position
     * is already occupied, all steps at or after that position are shifted forward by one.
     * The new step is always initialized with status [StepStatus.WAITING].
     *
     * @param phaseId The UUID of the phase to add the step to.
     * @param request The step creation request.
     * @return The created step.
     */
    @Operation(
        summary = "Create onboarding step",
        description = "Creates a new step within the specified phase at the given position. " +
            "Existing steps at or after the requested position are shifted forward by one. " +
            "The new step is always initialized with status WAITING.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Step created successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to create onboarding steps"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/phases/{phaseId}/steps")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createOnboardingStep(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
        @RequestBody request: CreateOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        return onboardingStepService.createOnboardingStepForPhaseId(phaseId, request)
    }

    /**
     * Returns a single onboarding step by its ID, including its direct tasks and resources.
     *
     * The returned object is at depth 2. It includes direct children at depth 3:
     * tasks and resources. No deeper nesting exists below those leaf nodes.
     *
     * @param stepId The UUID of the onboarding step.
     * @return The onboarding step with its tasks and resources.
     */
    @Operation(
        summary = "Get onboarding step by ID",
        description = "Returns a single onboarding step by its UUID. " +
            "The step is at hierarchy depth 2 and includes direct children at depth 3: " +
            "tasks and resources. No deeper nesting exists.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding step with direct tasks and resources"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to access this onboarding step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getOnboardingStep(
        @Parameter(description = "UUID of the onboarding step") @PathVariable stepId: UUID,
    ): GetOnboardingStepResponse {
        return onboardingStepService.getOnboardingStepById(stepId)
    }

    /**
     * Updates an existing onboarding step's metadata.
     *
     * All fields are replaced with the values from the request. If the position changes,
     * sibling steps are shifted automatically to maintain contiguous ordering. Status
     * changes are handled by separate endpoints.
     *
     * @param stepId The UUID of the step to update.
     * @param request The step update request.
     * @return The updated step.
     */
    @Operation(
        summary = "Update onboarding step",
        description = "Updates an existing onboarding step's metadata. " +
            "If the position changes, sibling steps are shifted automatically. " +
            "Status transitions are handled by separate endpoints.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Step updated successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to update this onboarding step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateOnboardingStep(
        @Parameter(description = "UUID of the onboarding step to update") @PathVariable stepId: UUID,
        @RequestBody request: UpdateOnboardingStepRequest,
    ): UpdateOnboardingStepResponse {
        return onboardingStepService.updateOnboardingStepById(stepId, request)
    }

    /**
     * Deletes an onboarding step and reorders remaining siblings.
     *
     * After deletion, all steps in the same phase with a position greater than the
     * deleted step's position are shifted back by one. The deleted object is at depth 2,
     * and any descendants below it are leaf nodes at depth 3: tasks and resources.
     *
     * @param stepId The UUID of the step to delete.
     */
    @Operation(
        summary = "Delete onboarding step",
        description = "Permanently deletes the specified onboarding step. " +
            "Subsequent sibling steps are shifted back by one to keep ordering contiguous. " +
            "The deleted object is at hierarchy depth 2, and any descendants below it are " +
            "limited to depth 3: tasks and resources.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Step deleted successfully"),
        ApiResponse(responseCode = "401", description = "Authentication required"),
        ApiResponse(responseCode = "403", description = "Insufficient role to delete this onboarding step"),
        ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/steps/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteOnboardingStepById(
        @Parameter(description = "UUID of the onboarding step to delete") @PathVariable stepId: UUID,
    ) {
        onboardingStepService.deleteOnboardingStepById(stepId)
    }

    /**
     * Returns an overview of the team's onboarding paths.
     *
     * This endpoint provides a paginated list of team members with their overall onboarding progress,
     * current phase, and current step. It supports filtering by search term, roles, and projects.
     *
     * @param search Optional search string to filter users by name or username.
     * @param roleIds Optional list of project role UUIDs to filter users by.
     * @param projectIds Optional list of project UUIDs to filter users by.
     * @param sortBy The sorting criteria (e.g., 'LONGEST_STEP', 'HIGHEST_PROGRESS'). Defaults to 'LONGEST_STEP'.
     * @param pageable Pagination parameters.
     * @return A paginated list of team overview user DTOs.
     */
    @Operation(
        summary = "Get team overview",
        description =
            "Returns a paginated overview of the team's onboarding paths, including overall progress " +
                "and current steps. " +
                "Supports filtering by search query, project roles, and projects.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Team overview returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access the team overview"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/team-overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getTeamOverview(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) roleIds: List<UUID>?,
        @RequestParam(required = false) projectIds: List<UUID>?,
        @RequestParam(
            required = false,
            defaultValue = "LONGEST_STEP",
        ) sortBy: String,
        pageable: Pageable,
    ): Page<TeamOverviewUserDto> {
        return onboardingPathService.getTeamOverview(search, roleIds, projectIds, sortBy, pageable)
    }
}
