package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.ReadOnboardingFeedbackResponse

fun OnboardingFeedback.toGetResponse(): GetOnboardingFeedbackResponse {
    return GetOnboardingFeedbackResponse(
        id = this.id,
        pageId = this.page?.id,
        helpful = this.helpful,
        comment = this.message,
        createdAt = this.createdAt,
    )
}

fun OnboardingFeedback.toAdminGetResponse(): GetAdminOnboardingFeedbackResponse {
    return GetAdminOnboardingFeedbackResponse(
        id = this.id,
        userId = this.userId,
        pageId = this.page?.id,
        pageTitle = this.page?.title,
        moduleId = this.page?.module?.id,
        competencyKey = this.page?.module?.competencyKey,
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
