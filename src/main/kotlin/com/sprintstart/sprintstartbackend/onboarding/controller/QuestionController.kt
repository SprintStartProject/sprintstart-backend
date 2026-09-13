package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetQuestionAttemptsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.SubmitQuestionAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.service.QuestionAttemptService
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
 * Exposes knowledge-check questions as individually answerable onboarding nodes.
 *
 * A question belongs to a phase and sits in that phase's subgraph next to its steps, so it
 * can block steps and be blocked by them. It is answered on its own and owns its attempt
 * history: passing it once is what completes it, and a wrong answer only leaves it open for
 * another try. The questions themselves are delivered with the user's path
 * (`GET /onboarding/me/path`); this controller covers answering and administration.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Questions",
    description = "Answer, edit, and review onboarding knowledge-check questions",
)
class QuestionController(
    private val questionAttemptService: QuestionAttemptService,
) {
//  ========================== Endpoints for users (/me/...) ==========================

    /**
     * Submits the authenticated user's answer to one question.
     *
     * The attempt is graded and stored. The response reveals whether the answer was correct
     * together with the correct answer, the explanation, and the AI's feedback for short-text
     * answers, and reports the question's new status.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @param questionId Identifier of the question being answered.
     * @param request The user's answer.
     * @return The graded attempt.
     */
    @Operation(
        summary = "Submit an answer to an onboarding question",
        description = "Grades and stores one answer. The result reveals the correct answer and " +
            "explanation, and reports whether the question is now passed. A correct answer can " +
            "unblock waiting steps or questions and may complete the onboarding journey.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Answer graded and stored successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to answer this question"),
            ApiResponse(
                responseCode = "404",
                description = "No user found, or the question is not part of the authenticated user's path",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/questions/{questionId}/attempts")
    @PreAuthorize("hasRole('USER')")
    fun submitQuestionAttemptForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID of the question being answered")
        @PathVariable questionId: UUID,
        @Valid @RequestBody request: SubmitQuestionAttemptRequest,
    ): SubmitQuestionAttemptResponse {
        return questionAttemptService.submitQuestionAttemptForMe(jwt.subject, questionId, request)
    }

//  ========================== Endpoints for admins ==========================

    /**
     * Returns the questions of a phase including correct answers.
     *
     * Intended for admin-facing onboarding management screens where questions are created
     * and edited.
     *
     * @param phaseId Identifier of the phase whose questions should be loaded.
     * @return The questions including correct answers.
     */
    @Operation(
        summary = "Get a phase's questions for editing",
        description = "Returns the knowledge-check questions of the phase including correct answers, " +
            "for admin-facing editing screens.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Questions returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access these questions"),
            ApiResponse(responseCode = "404", description = "No phase found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/phases/{phaseId}/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getPhaseQuestions(
        @Parameter(description = "UUID of the phase whose questions should be returned")
        @PathVariable phaseId: UUID,
    ): GetPhaseQuestionsResponse {
        return questionAttemptService.getPhaseQuestions(phaseId)
    }

    /**
     * Replaces the questions of a phase.
     *
     * Questions sent with the ID of an existing one are updated in place and keep that ID,
     * questions without a known ID are created, and questions the request omits are deleted
     * together with the blocker edges pointing at them. Options are matched by ID the same
     * way. Sending existing IDs back matters — a recreated question loses the stored attempts
     * pointing at it.
     *
     * @param phaseId Identifier of the phase whose questions should be replaced.
     * @param request The new questions.
     * @return The stored questions including correct answers.
     */
    @Operation(
        summary = "Replace a phase's questions",
        description = "Replaces the knowledge-check questions of the phase. Questions and options sent " +
            "with the ID of an existing entry are updated in place and keep that ID; entries without a " +
            "known ID are created, and entries the request omits are deleted together with their " +
            "blocker edges. Multiple choice questions need at least 2 options and 1 correct option; " +
            "short text questions need a correctAnswer.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Questions replaced successfully"),
            ApiResponse(responseCode = "400", description = "A question is invalid for its type"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to edit these questions"),
            ApiResponse(responseCode = "404", description = "No phase found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/phases/{phaseId}/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun replacePhaseQuestions(
        @Parameter(description = "UUID of the phase whose questions should be replaced")
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdatePhaseQuestionsRequest,
    ): GetPhaseQuestionsResponse {
        return questionAttemptService.replacePhaseQuestions(phaseId, request)
    }

    /**
     * Returns every attempt a user made on one question.
     *
     * Allows admins, PMs, and HR to review how a user arrived at their answer, including the
     * wrong tries that came before a pass.
     *
     * @param userId Identifier of the user whose attempts should be returned.
     * @param questionId Identifier of the question whose attempts should be returned.
     * @return The user's attempts, newest first.
     */
    @Operation(
        summary = "Get a user's attempts on a question",
        description = "Returns every attempt the user submitted for the question, newest first, " +
            "so admins, PMs, or HR can review the answers.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Attempts returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access these attempts"),
            ApiResponse(
                responseCode = "404",
                description = "No user found, or the question is not part of that user's path",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/questions/{questionId}/attempts")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getQuestionAttemptsForUser(
        @Parameter(description = "UUID of the user whose attempts should be returned")
        @PathVariable userId: UUID,
        @Parameter(description = "UUID of the question whose attempts should be returned")
        @PathVariable questionId: UUID,
    ): GetQuestionAttemptsResponse {
        return questionAttemptService.getQuestionAttemptsForUser(userId, questionId)
    }
}
