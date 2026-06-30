package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.ReadOnboardingFeedbackResponse

fun OnboardingFeedback.toGetResponse(): GetOnboardingFeedbackResponse {
    return GetOnboardingFeedbackResponse(
        id = this.id,
        stepId = this.step?.id,
        helpful = this.helpful,
        comment = this.message,
        createdAt = this.createdAt,
    )
}

fun OnboardingFeedback.toAdminGetResponse(): GetAdminOnboardingFeedbackResponse {
    return GetAdminOnboardingFeedbackResponse(
        id = this.id,
        userId = this.userId,
        stepId = this.step?.id,
        stepTitle = this.step?.title,
        message = this.message,
        read = this.read,
        createdAt = this.createdAt,
    )
}

fun OnboardingFeedback.toReadResponse(): ReadOnboardingFeedbackResponse {
    return ReadOnboardingFeedbackResponse(
        id = this.id,
        read = this.read,
    )
}
