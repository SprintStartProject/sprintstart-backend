package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.feedback.CreateOnboardingFeedbackRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.ReadOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingFeedbackService
import io.swagger.v3.oas.annotations.Parameter
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
class OnboardingFeedbackController(
    private val onboardingFeedbackService: OnboardingFeedbackService,
) {
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/feedback")
    @PreAuthorize("hasRole('USER')")
    fun getAllFeedbackForMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): List<GetOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedbackForMe(jwt.subject)
    }

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
class OnboardingFeedbackAdminController(
    private val onboardingFeedbackService: OnboardingFeedbackService,
) {
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllFeedback(): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedback()
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllFeedbackByUserId(
        @PathVariable userId: UUID,
    ): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedbackByUserId(userId)
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}/feedback")
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllFeedbackByStepId(
        @PathVariable stepId: UUID,
    ): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackService.getAllFeedbackByStepId(stepId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/feedback/{feedbackId}/read")
    @PreAuthorize("hasRole('ADMIN')")
    fun markFeedbackAsRead(
        @PathVariable feedbackId: UUID,
    ): ReadOnboardingFeedbackResponse {
        return onboardingFeedbackService.markFeedbackAsRead(feedbackId)
    }
}
