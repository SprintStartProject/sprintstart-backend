package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSkip
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetAllOnboardingSkipsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.ReviewOnboardingSkipResponse

fun OnboardingSkip.toGetResponse(): GetOnboardingSkipResponse {
    return GetOnboardingSkipResponse(
        id = this.id,
        stepId = this.step.id,
        status = this.status,
        reason = this.reason,
        reviewComment = this.reviewComment,
        createdAt = this.createdAt,
        resolvedAt = this.resolvedAt,
    )
}

fun OnboardingSkip.toGetAllResponse(): GetAllOnboardingSkipsResponse {
    return GetAllOnboardingSkipsResponse(
        id = this.id,
        stepId = this.step.id,
        status = this.status,
        reason = this.reason,
        reviewComment = this.reviewComment,
        createdAt = this.createdAt,
        resolvedAt = this.resolvedAt,
    )
}

fun OnboardingSkip.toCreateResponse(): CreateOnboardingSkipResponse {
    return CreateOnboardingSkipResponse(
        id = this.id,
        stepId = this.step.id,
        status = this.status,
        reason = this.reason,
        reviewComment = this.reviewComment,
        createdAt = this.createdAt,
        reviewedAt = this.resolvedAt,
    )
}

fun OnboardingSkip.toReviewResponse(): ReviewOnboardingSkipResponse {
    return ReviewOnboardingSkipResponse(
        id = this.id,
        stepId = this.step.id,
        status = this.status,
        reason = this.reason,
        reviewComment = this.reviewComment.orEmpty(),
        createdAt = this.createdAt,
        resolvedAt = requireNotNull(this.resolvedAt) {
            "Onboarding skip ${this.id} must be resolved before creating a review response"
        },
    )
}
