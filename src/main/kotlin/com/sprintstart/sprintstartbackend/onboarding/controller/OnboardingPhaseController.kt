package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
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
 * REST controller for managing onboarding phases.
 *
 * Exposes endpoints under `/api/v1/onboarding` for creating, retrieving, updating,
 * and deleting onboarding phases. Phases are the second level of the onboarding hierarchy,
 * sitting directly below a path and above steps.
 *
 * Phases are ordered within their parent path by a numeric position. Insertions, updates,
 * and deletions automatically shift sibling phases to maintain a contiguous, gap-free ordering.
 *
 * Access rules:
 * - `GET /phases` — admin only (flat listing of all phases across all paths)
 * - All other endpoints — accessible to users with the appropriate role
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - Phases", description = "Create, retrieve, update, and delete onboarding phases")
class OnboardingPhaseController(
    val onboardingService: OnboardingService,
) {
    /**
     * Creates a new phase within the specified path at the given position.
     *
     * Phases are ordered within a path by a numeric position. If the requested position
     * is already occupied, all phases at or after that position are shifted one place
     * forward to make room. This means position values are not stable identifiers and
     * should not be stored externally.
     *
     * @param pathId The UUID of the path to add the phase to.
     * @param request The phase creation request containing position, title, and description.
     * @return The created phase.
     */
    @Operation(
        summary = "Create onboarding phase",
        description = "Creates a new phase within the specified path at the given position. " +
            "If the position is already occupied, all phases at or after that position are shifted forward by one. " +
            "Position values are not stable and should not be used as external references.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Phase created successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/paths/{pathId}/phases")
    fun createOnboardingPhase(
        @Parameter(description = "UUID of the onboarding path") @PathVariable pathId: UUID,
        @RequestBody request: CreateOnboardingPhaseRequest,
    ): CreateOnboardingPhaseResponse {
        return onboardingService.createOnboardingPhaseForPathId(pathId, request)
    }

    /**
     * Returns all onboarding phases across all paths.
     *
     * This is a flat listing with no nested content. Steps within each phase are not
     * included. Intended for administrative use or bulk exports.
     *
     * @return A flat list of all onboarding phases.
     */
    @Operation(
        summary = "Get all onboarding phases",
        description = "Returns a flat list of all onboarding phases across all paths. " +
            "No nested content is included. Intended for administrative overviews.",
    )
    @ApiResponse(responseCode = "200", description = "Flat list of all onboarding phases")
    @GetMapping("/phases")
    fun getOnboardingPhases(): List<GetOnboardingPhasesResponse> {
        return onboardingService.getOnboardingPhases()
    }

    /**
     * Returns all phases belonging to a specific path, including their direct steps.
     *
     * Returns one level of nesting: each phase in the response includes its direct steps,
     * but tasks and resources within those steps are not included. Phases are ordered
     * by their position within the path.
     *
     * @param pathId The UUID of the onboarding path.
     * @return An ordered list of phases with their direct steps.
     */
    @Operation(
        summary = "Get phases by path ID",
        description = "Returns all onboarding phases belonging to the specified path, ordered by position. " +
            "Each phase includes its direct steps (one level of nesting). " +
            "Tasks and resources within those steps are not included.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Ordered list of phases with direct steps"),
        ApiResponse(responseCode = "404", description = "No onboarding path found with the given ID"),
    )
    @GetMapping("/paths/{pathId}/phases")
    fun getAllOnboardingPhasesByPathId(
        @Parameter(description = "UUID of the onboarding path") @PathVariable pathId: UUID,
    ): List<GetOnboardingPhaseResponse> {
        return onboardingService.getOnboardingPhasesByPathId(pathId)
    }

    /**
     * Returns a single onboarding phase by its ID, including its direct steps.
     *
     * Returns one level of nesting: the phase and its direct steps are included,
     * but tasks and resources within those steps are not.
     *
     * @param phaseId The UUID of the onboarding phase.
     * @return The onboarding phase with its direct steps.
     */
    @Operation(
        summary = "Get onboarding phase by ID",
        description = "Returns a single onboarding phase by its UUID. " +
            "Includes one level of nesting: the phase's direct steps are " +
            "included, but tasks and resources within those steps are not.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Onboarding phase with direct steps"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @GetMapping("/phases/{phaseId}")
    fun getOnboardingPhase(
        @Parameter(description = "UUID of the onboarding phase") @PathVariable phaseId: UUID,
    ): GetOnboardingPhaseResponse {
        return onboardingService.getOnboardingPhase(phaseId)
    }

    /**
     * Updates an existing onboarding phase, including its position within the path.
     *
     * All fields are replaced with the values from the request. If the position changes,
     * sibling phases between the old and new positions are automatically shifted to maintain
     * a contiguous, gap-free ordering. Moving a phase forward (higher position) shifts
     * intermediary phases back by one; moving it backward shifts them forward by one.
     *
     * @param phaseId The UUID of the phase to update.
     * @param request The phase update request containing the new position, title, and description.
     * @return The updated phase.
     */
    @Operation(
        summary = "Update onboarding phase",
        description = "Updates all fields of an existing onboarding phase, including its position. " +
            "If the position changes, sibling phases between the old and new positions are shifted " +
            "automatically to maintain contiguous ordering. Moving forward shifts intermediaries" +
            " back; moving backward shifts them forward.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Phase updated successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @PutMapping("/phases/{phaseId}")
    fun updateOnboardingPhase(
        @Parameter(description = "UUID of the onboarding phase to update") @PathVariable phaseId: UUID,
        @RequestBody request: UpdateOnboardingPhaseRequest,
    ): UpdateOnboardingPhaseResponse {
        return onboardingService.updateOnboardingPhase(phaseId, request)
    }

    /**
     * Deletes an onboarding phase and reorders remaining siblings.
     *
     * After deletion, all phases in the same path with a position greater than the
     * deleted phase's position are shifted back by one, keeping the ordering contiguous.
     * All child steps, tasks, and resources are removed via cascade.
     *
     * @param phaseId The UUID of the phase to delete.
     */
    @Operation(
        summary = "Delete onboarding phase",
        description = "Permanently deletes the specified onboarding phase. " +
            "Subsequent sibling phases are shifted back by one to keep ordering contiguous. " +
            "All child steps, tasks, and resources are removed via cascade.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Phase deleted successfully"),
        ApiResponse(responseCode = "404", description = "No onboarding phase found with the given ID"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/phases/{phaseId}")
    fun deleteOnboardingPhaseForPathId(
        @Parameter(description = "UUID of the onboarding phase to delete") @PathVariable phaseId: UUID,
    ) {
        onboardingService.deleteOnboardingPhase(phaseId)
    }
}
