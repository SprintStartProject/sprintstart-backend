package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.feedback.CreateOnboardingFeedbackRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.ReadOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingFeedbackService
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding/me")
@Tag(name = "Onboarding - User Feedback", description = "Allows management of user feedback on parts of onboarding")
class OnboardingFeedbackController(
    private val onboardingFeedbackService: OnboardingFeedbackService,
) {
    /**
     * Retrieves all user feedback for all onboarding path steps.
     */
    @Operation(summary = "Get all onboarding feedbacks", description = "Get all onboarding feedbacks")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved all onboarding feedbacks"),
            ApiResponse(responseCode = "401", description = "Unauthorized to access this resource"),
            ApiResponse(responseCode = "403", description = "Forbidden to access this resource"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/feedback")
    @PreAuthorize("hasRole('USER')")
    fun getAllFeedbackForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): List<GetOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedbackForMe(jwt.subject)
    }

    /**
     * Retrieves all user feedback given on a given onboarding path step.
     */
    @Operation(summary = "Get feedback for step", description = "Retrieve all feedback for a given onboarding step")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved all feedbacks for this step"),
            ApiResponse(responseCode = "401", description = "Unauthorized to access this resource"),
            ApiResponse(responseCode = "403", description = "Forbidden to access this resource"),
            ApiResponse(responseCode = "404", description = "Step with given ID not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}/feedback")
    @PreAuthorize("hasRole('USER')")
    fun getFeedbackByStepIdForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable stepId: UUID,
    ): List<GetOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getFeedbackByStepIdForMe(jwt.subject, stepId)
    }

    /**
     * Allows the addition of user feedback on a given onboarding step.
     */
    @Operation(summary = "Add feedback for step", description = "Adds new feedback for a given onboarding step")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully added feedback for this step"),
            ApiResponse(responseCode = "401", description = "Unauthorized to access this resource"),
            ApiResponse(responseCode = "403", description = "Forbidden to access this resource"),
            ApiResponse(responseCode = "404", description = "Step with given ID not found"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/feedback")
    @PreAuthorize("hasRole('USER')")
    fun createFeedbackForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateOnboardingFeedbackRequest,
    ): GetOnboardingFeedbackResponse {
        return onboardingFeedbackService.createFeedbackForMe(jwt.subject, request)
    }
}

@RestController
@RequestMapping("/api/v1/admin/onboarding")
@Tag(name = "Onboarding - User Feedback (Admin)", description = "Allows management of user feedback for admins")
class OnboardingFeedbackAdminController(
    private val onboardingFeedbackService: OnboardingFeedbackService,
) {
    /**
     * Retrieves all user feedback for all users.
     */
    @Operation(
        summary = "Retrieve all feedbacks",
        description = "Get all onboarding path step feedbacks from all users",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved all feedbacks for this user"),
            ApiResponse(responseCode = "401", description = "Unauthorized to access this resource"),
            ApiResponse(responseCode = "403", description = "Forbidden to access this resource"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllFeedback(): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedback()
    }

    /**
     * Retrieves all feedback given by a specific given user.
     */
    @Operation(
        summary = "Retrieves all feedback of a user",
        description = "Retrieves all feedback given by a specific user",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved all feedbacks for this user"),
            ApiResponse(responseCode = "401", description = "Unauthorized to access this resource"),
            ApiResponse(responseCode = "403", description = "Forbidden to access this resource"),
            ApiResponse(responseCode = "404", description = "User with given ID not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllFeedbackByUserId(
        @PathVariable userId: UUID,
    ): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedbackByUserId(userId)
    }

    /**
     * Retrieves all user feedback given on a specific onboarding path step.
     */
    @Operation(
        summary = "Get all feedback on a step",
        description = "Retrieves all user feedback on a given onboarding path step",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully retrieved all feedbacks for this step"),
            ApiResponse(responseCode = "401", description = "Unauthorized to access this resource"),
            ApiResponse(responseCode = "403", description = "Forbidden to access this resource"),
            ApiResponse(responseCode = "404", description = "Step with given ID not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllFeedbackByStepId(
        @PathVariable stepId: UUID,
    ): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedbackByStepId(stepId)
    }

    /**
     * Marks a user feedback as read by an admin.
     */
    @Operation(summary = "Marks user feedback as read", description = "Marks a user feedback as read by an admin")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Successfully marked user feedback as read"),
            ApiResponse(responseCode = "401", description = "Unauthorized to access this resource"),
            ApiResponse(responseCode = "403", description = "Forbidden to access this resource"),
            ApiResponse(responseCode = "404", description = "Feedback with given ID not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/feedback/{feedbackId}/read")
    @PreAuthorize("hasRole('ADMIN')")
    fun markFeedbackAsRead(
        @PathVariable feedbackId: UUID,
    ): ReadOnboardingFeedbackResponse {
        return onboardingFeedbackService.markFeedbackAsRead(feedbackId)
    }
}
