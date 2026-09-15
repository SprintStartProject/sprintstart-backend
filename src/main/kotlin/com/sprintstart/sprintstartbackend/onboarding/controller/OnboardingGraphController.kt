package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ArrangeOnboardingGraphRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.CreateConnectedOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ReplaceOnboardingBlockersRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.graph.OnboardingBlockersResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingGraphService
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingStepPlacementService
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Edits the graphs of an onboarding path: where its nodes sit, what waits on what, and steps added
 * straight into a phase graph.
 *
 * Layout is open to the path's owner (`/me/...`); edges and new connected steps are PM, HR and
 * admin decisions, since they change what unlocks when. See [OnboardingGraphService].
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - Graph", description = "Arrange and connect the phase and step graphs of a path")
class OnboardingGraphController(
    private val onboardingGraphService: OnboardingGraphService,
    private val onboardingStepPlacementService: OnboardingStepPlacementService,
) {
//  ========================== Endpoints for users (/me/...) ==========================

    /**
     * Stores canvas positions for the phases of the caller's own path.
     *
     * @param request The phases to move and where to.
     */
    @Operation(summary = "Arrange my path graph", description = "Stores canvas positions of my path's phases.")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Positions stored"),
        ApiResponse(responseCode = "400", description = "A position names something that is not a phase of the path"),
        ApiResponse(responseCode = "404", description = "No onboarding path found"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/me/path/graph")
    @PreAuthorize("hasRole('USER')")
    fun arrangeMyPath(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: ArrangeOnboardingGraphRequest,
    ) {
        onboardingGraphService.arrangePathForMe(jwt.subject, request)
    }

    /**
     * Stores canvas positions for the steps and questions of one phase of the caller's own path.
     *
     * @param phaseId The phase whose graph is arranged.
     * @param request The nodes to move and where to.
     */
    @Operation(
        summary = "Arrange a phase graph of my path",
        description = "Stores canvas positions of a phase's steps and questions.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Positions stored"),
        ApiResponse(responseCode = "400", description = "A position names something that is not a node of the phase"),
        ApiResponse(responseCode = "404", description = "No such phase on my path"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/me/phases/{phaseId}/graph")
    @PreAuthorize("hasRole('USER')")
    fun arrangeMyPhase(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the phase") @PathVariable phaseId: UUID,
        @RequestBody request: ArrangeOnboardingGraphRequest,
    ) {
        onboardingGraphService.arrangePhaseForMe(jwt.subject, phaseId, request)
    }

//  ========================== Endpoints for PM, HR and admins ==========================

    /**
     * Stores canvas positions for the phases of a user's path.
     *
     * @param userId The user whose path is arranged.
     * @param request The phases to move and where to.
     */
    @Operation(summary = "Arrange a user's path graph", description = "Stores canvas positions of the phases.")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Positions stored"),
        ApiResponse(responseCode = "400", description = "A position names something that is not a phase of the path"),
        ApiResponse(responseCode = "403", description = "Insufficient role"),
        ApiResponse(responseCode = "404", description = "No onboarding path found"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/users/{userId}/path/graph")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun arrangeUserPath(
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
        @RequestBody request: ArrangeOnboardingGraphRequest,
    ) {
        onboardingGraphService.arrangePathForUser(userId, request)
    }

    /**
     * Stores canvas positions for the steps and questions of any phase.
     *
     * @param phaseId The phase whose graph is arranged.
     * @param request The nodes to move and where to.
     */
    @Operation(summary = "Arrange a phase graph", description = "Stores canvas positions of steps and questions.")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Positions stored"),
        ApiResponse(responseCode = "400", description = "A position names something that is not a node of the phase"),
        ApiResponse(responseCode = "403", description = "Insufficient role"),
        ApiResponse(responseCode = "404", description = "No such phase"),
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/phases/{phaseId}/graph")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun arrangePhase(
        @Parameter(description = "UUID of the phase") @PathVariable phaseId: UUID,
        @RequestBody request: ArrangeOnboardingGraphRequest,
    ) {
        onboardingGraphService.arrangePhaseById(phaseId, request)
    }

    /**
     * Replaces what a step or knowledge-check question waits on.
     *
     * @param nodeId The step or question.
     * @param request Every item it waits on from now on.
     * @return The node's blockers after the change.
     */
    @Operation(
        summary = "Replace the blockers of a step or question",
        description = "Sets every item of the same phase the node waits on. Rejects loops.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Blockers replaced"),
        ApiResponse(responseCode = "400", description = "A blocker is outside the phase, the node itself, or a loop"),
        ApiResponse(responseCode = "403", description = "Insufficient role"),
        ApiResponse(responseCode = "404", description = "No such step or question"),
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/nodes/{nodeId}/blockers")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun replaceNodeBlockers(
        @Parameter(description = "UUID of the step or question") @PathVariable nodeId: UUID,
        @RequestBody request: ReplaceOnboardingBlockersRequest,
    ): OnboardingBlockersResponse {
        return onboardingGraphService.replaceNodeBlockers(nodeId, request)
    }

    /**
     * Replaces the phases a phase waits on.
     *
     * @param phaseId The phase.
     * @param request Every phase of the same path it waits on from now on.
     * @return The phase's blockers after the change.
     */
    @Operation(
        summary = "Replace the blockers of a phase",
        description = "Sets every phase of the same path the phase waits on. Rejects loops.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Blockers replaced"),
        ApiResponse(responseCode = "400", description = "A blocker is outside the path, the phase itself, or a loop"),
        ApiResponse(responseCode = "403", description = "Insufficient role"),
        ApiResponse(responseCode = "404", description = "No such phase"),
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/phases/{phaseId}/blockers")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun replacePhaseBlockers(
        @Parameter(description = "UUID of the phase") @PathVariable phaseId: UUID,
        @RequestBody request: ReplaceOnboardingBlockersRequest,
    ): OnboardingBlockersResponse {
        return onboardingGraphService.replacePhaseBlockers(phaseId, request)
    }

    /**
     * Creates a step in a phase and connects it into the phase graph in one go.
     *
     * @param phaseId The phase the step is added to.
     * @param request The step, what it waits on, what it unlocks, and optionally where it sits.
     * @return The created step.
     */
    @Operation(
        summary = "Create a connected step",
        description = "Creates a step that waits on `waitsOn` and is waited on by `unlocks`; a direct edge " +
            "between the two is replaced by one through the new step.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Step created and connected"),
        ApiResponse(responseCode = "400", description = "An item is outside the phase, or the placement is a loop"),
        ApiResponse(responseCode = "403", description = "Insufficient role"),
        ApiResponse(responseCode = "404", description = "No such phase"),
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/phases/{phaseId}/steps/connected")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createConnectedStep(
        @Parameter(description = "UUID of the phase") @PathVariable phaseId: UUID,
        @RequestBody request: CreateConnectedOnboardingStepRequest,
    ): CreateOnboardingStepResponse {
        return onboardingStepPlacementService.createConnectedStepForPhase(
            phaseId = phaseId,
            request = request.step,
            waitsOn = request.waitsOn,
            unlocks = request.unlocks,
            graphX = request.graphX,
            graphY = request.graphY,
        )
    }
}
