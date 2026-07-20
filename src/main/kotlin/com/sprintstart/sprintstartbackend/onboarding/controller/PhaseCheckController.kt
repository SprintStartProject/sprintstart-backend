package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitPhaseCheckAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdatePhaseCheckRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckAttemptsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.SubmitPhaseCheckAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.service.PhaseCheckService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
 * Exposes phase-level knowledge check endpoints.
 *
 * Knowledge checks belong to onboarding phases (depth 1), not to individual steps.
 * A phase only counts as completed from the user's perspective once all its steps
 * are finished or skipped and, if a check exists, the check has been passed. The
 * next phase stays locked until then.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Phase Checks",
    description = "Take, edit, and review phase-level knowledge checks",
)
class PhaseCheckController(
    private val phaseCheckService: PhaseCheckService,
) {
//  ========================== Endpoints for users (/me/...) ==========================

    /**
     * Returns the knowledge check for a phase in the authenticated user's path.
     *
     * The response contains everything needed to render the check UI but never
     * exposes correct answers; those are only included in submit results.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the phase whose check should be loaded.
     * @return The check questions without correct answers.
     */
    @Operation(
        summary = "Get current user's phase knowledge check",
        description = "Returns the knowledge check questions of the phase for the authenticated user. " +
            "Correct answers are not exposed; they are only revealed in the submit result.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Knowledge check returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this knowledge check"),
            ApiResponse(
                responseCode = "404",
                description = "No user or phase found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/phases/{phaseId}/checks")
    @PreAuthorize("hasRole('USER')")
    fun getPhaseCheckForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the phase whose knowledge check should be returned")
        @PathVariable phaseId: UUID,
    ): GetPhaseCheckForUserResponse {
        return phaseCheckService.getPhaseCheckForMe(jwt.subject, phaseId)
    }

    /**
     * Submits the authenticated user's answers for a phase knowledge check.
     *
     * The attempt is graded and stored. The response reveals per question whether
     * the answer was correct together with the correct answer and explanation.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param phaseId Identifier of the phase whose check is being taken.
     * @param request The user's answers.
     * @return The graded attempt including per-question results.
     */
    @Operation(
        summary = "Submit current user's phase knowledge check attempt",
        description = "Grades and stores a knowledge check attempt for the phase. The result includes " +
            "whether the attempt passed and the correct answers per question.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Attempt graded and stored successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to submit this knowledge check"),
            ApiResponse(
                responseCode = "404",
                description = "No user, phase, or knowledge check found for the authenticated user",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/phases/{phaseId}/checks/attempts")
    @PreAuthorize("hasRole('USER')")
    fun submitPhaseCheckAttemptForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the phase whose knowledge check is being taken")
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: SubmitPhaseCheckAttemptRequest,
    ): SubmitPhaseCheckAttemptResponse {
        return phaseCheckService.submitPhaseCheckAttemptForMe(jwt.subject, phaseId, request)
    }

//  ========================== Endpoints for admins ==========================

    /**
     * Returns the knowledge check of a phase including correct answers.
     *
     * Intended for admin-facing onboarding management screens where checks are
     * created and edited.
     *
     * @param phaseId Identifier of the phase whose check should be loaded.
     * @return The check questions including correct answers.
     */
    @Operation(
        summary = "Get phase knowledge check for editing",
        description = "Returns the knowledge check questions of the phase including correct answers, " +
            "for admin-facing editing screens.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Knowledge check returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this knowledge check"),
            ApiResponse(responseCode = "404", description = "No phase found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/phases/{phaseId}/checks")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getPhaseCheck(
        @Parameter(description = "UUID of the phase whose knowledge check should be returned")
        @PathVariable phaseId: UUID,
    ): GetPhaseCheckResponse {
        return phaseCheckService.getPhaseCheck(phaseId)
    }

    /**
     * Replaces the knowledge check questions of a phase.
     *
     * All existing questions are replaced by the submitted ones. Stored attempts
     * remain untouched as history.
     *
     * @param phaseId Identifier of the phase whose check should be replaced.
     * @param request The new check questions.
     * @return The stored check questions including correct answers.
     */
    @Operation(
        summary = "Replace phase knowledge check",
        description = "Replaces all knowledge check questions of the phase. Multiple choice questions need " +
            "at least 2 options and 1 correct option; short text questions need a correctAnswer.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Knowledge check replaced successfully"),
            ApiResponse(responseCode = "400", description = "A question is invalid for its type"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to edit this knowledge check"),
            ApiResponse(responseCode = "404", description = "No phase found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/phases/{phaseId}/checks")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun replacePhaseCheck(
        @Parameter(description = "UUID of the phase whose knowledge check should be replaced")
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdatePhaseCheckRequest,
    ): GetPhaseCheckResponse {
        return phaseCheckService.replacePhaseCheck(phaseId, request)
    }

    /**
     * Returns all submitted knowledge check attempts of a user for one phase.
     *
     * Allows admins, PMs, and HR to review a user's check results including the
     * given answers.
     *
     * @param userId Identifier of the user whose attempts should be returned.
     * @param phaseId Identifier of the phase whose attempts should be returned.
     * @return The user's attempts, newest first.
     */
    @Operation(
        summary = "Get a user's phase knowledge check attempts",
        description = "Returns all submitted knowledge check attempts of the user for the phase, " +
            "newest first, so admins, PMs, or HR can review the results.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Attempts returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access these attempts"),
            ApiResponse(responseCode = "404", description = "No user or phase found with the given IDs"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/phases/{phaseId}/checks/attempts")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getPhaseCheckAttemptsForUser(
        @Parameter(description = "UUID of the user whose attempts should be returned")
        @PathVariable userId: UUID,
        @Parameter(description = "UUID of the phase whose attempts should be returned")
        @PathVariable phaseId: UUID,
    ): GetPhaseCheckAttemptsResponse {
        return phaseCheckService.getPhaseCheckAttemptsForUser(userId, phaseId)
    }
}
