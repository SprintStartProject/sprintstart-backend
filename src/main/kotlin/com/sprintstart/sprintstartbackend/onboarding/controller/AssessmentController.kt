package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.assessment.AnswerAssessmentRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.assessment.AnswerAssessmentResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.assessment.GetAssessmentStatusResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.assessment.StartAssessmentResponse
import com.sprintstart.sprintstartbackend.onboarding.service.AssessmentService
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Exposes the turn-based adaptive skill-assessment interview (Seam 2) for the authenticated user.
 *
 * The conversation is stateless on the AI side; this controller's [AssessmentService] owns the
 * session and replays its transcript on every turn.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Assessment",
    description = "Adaptive skill-chat interview that places the authenticated user on the competency graph",
)
class AssessmentController(
    private val assessmentService: AssessmentService,
) {
    /**
     * Returns whether the authenticated user has ever completed a skill assessment for one project.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param projectId The project to check completion for. Assessment is per-project.
     * @return Whether a completed assessment session exists for the user on this project.
     */
    @Operation(
        summary = "Get the current user's skill-assessment status for one project",
        description = "Returns whether the user has ever completed the adaptive interview for " +
            "this project. Drives the client-side 'needs assessment' gate.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Status returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to read this status"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/assessment/status")
    @PreAuthorize("hasRole('USER')")
    fun getAssessmentStatusForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): GetAssessmentStatusResponse {
        return GetAssessmentStatusResponse(
            completed = assessmentService.hasCompletedAssessment(jwt.subject, projectId),
        )
    }

    /**
     * Starts (or resumes) the authenticated user's skill-assessment interview for one project.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param projectId The project to run the interview for. Assessment is per-project.
     * @return The session id and the question to show next, or `done=true` with no question if the
     * project has nothing configured to assess yet.
     */
    @Operation(
        summary = "Start or resume the current user's skill assessment for one project",
        description = "Starts a new adaptive interview scoped to this project's live competency " +
            "modules, or resumes the user's existing in-progress session for it instead of " +
            "starting a duplicate.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Session started or resumed successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to start an assessment"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated user"),
            ApiResponse(responseCode = "502", description = "The AI service returned no question"),
            ApiResponse(responseCode = "503", description = "The AI service is temporarily unavailable"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/assessment/start")
    @PreAuthorize("hasRole('USER')")
    suspend fun startAssessmentForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): StartAssessmentResponse {
        return assessmentService.startAssessment(jwt.subject, projectId)
    }

    /**
     * Submits the authenticated user's answer for the currently open turn.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param request The session being answered and the candidate's answer.
     * @return The next question, or `done=true` once a final placement has been written.
     */
    @Operation(
        summary = "Answer the current turn of the user's skill assessment",
        description = "Submits the candidate's answer for the open turn. Returns the next " +
            "question, or done=true once the AI interviewer has returned a final placement " +
            "and it has been written to the competency ledger.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Turn processed successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to answer this assessment"),
            ApiResponse(
                responseCode = "404",
                description = "No user or assessment session found for the authenticated user",
            ),
            ApiResponse(responseCode = "409", description = "The session has no open turn to answer"),
            ApiResponse(responseCode = "502", description = "The AI service returned no question"),
            ApiResponse(responseCode = "503", description = "The AI service is temporarily unavailable"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/assessment/answer")
    @PreAuthorize("hasRole('USER')")
    suspend fun answerAssessmentForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: AnswerAssessmentRequest,
    ): AnswerAssessmentResponse {
        return assessmentService.answerAssessment(jwt.subject, request.sessionId, request.answer)
    }
}
