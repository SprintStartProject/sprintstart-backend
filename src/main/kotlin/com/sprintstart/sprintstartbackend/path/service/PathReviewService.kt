package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.PathReviewApi
import com.sprintstart.sprintstartbackend.path.external.model.PathElement
import com.sprintstart.sprintstartbackend.path.external.model.PathElementKind
import com.sprintstart.sprintstartbackend.path.external.model.PathSummary
import com.sprintstart.sprintstartbackend.path.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.path.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.path.model.response.feedback.ReadOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.path.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.skip.GetOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.path.model.response.skip.ReviewOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.shared.ids.FeedbackId
import com.sprintstart.sprintstartbackend.shared.ids.SkipId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Service
import java.util.UUID

/** [PathReviewApi] over the services that own each answer. */
@Service
class PathReviewService(
    private val onboardingPathService: OnboardingPathService,
    private val pathElements: PathElements,
    private val onboardingSkipService: OnboardingSkipService,
    private val onboardingFeedbackService: OnboardingFeedbackService,
) : PathReviewApi {
    override fun pathOf(userId: UserId): GetOnboardingPathForUserResponse =
        onboardingPathService.getOnboardingPathByUserId(userId.value)

    override fun summaryOf(userId: UserId): PathSummary? = pathElements.pathOf(userId.value)

    override fun element(kind: PathElementKind, id: UUID): PathElement? = pathElements.find(kind, id)

    override fun resetPath(userId: UserId) = onboardingPathService.deleteOnboardingPathByUserId(userId.value)

    override fun skipsOf(userId: UserId): List<GetOnboardingSkipResponse> =
        onboardingSkipService.getAllSkipsByUserId(userId.value)

    override fun skip(skipId: SkipId): GetOnboardingSkipResponse = onboardingSkipService.getSkipById(skipId.value)

    override fun acceptSkip(skipId: SkipId, request: ReviewOnboardingSkipRequest): ReviewOnboardingSkipResponse =
        onboardingSkipService.acceptSkipById(skipId.value, request)

    override fun denySkip(skipId: SkipId, request: ReviewOnboardingSkipRequest): ReviewOnboardingSkipResponse =
        onboardingSkipService.denySkipById(skipId.value, request)

    override fun deleteSkip(skipId: SkipId) = onboardingSkipService.deleteSkipById(skipId.value)

    override fun feedbackOf(userId: UserId): List<GetAdminOnboardingFeedbackResponse> =
        onboardingFeedbackService.getAllFeedbackByUserId(userId.value)

    override fun markFeedbackRead(feedbackId: FeedbackId): ReadOnboardingFeedbackResponse =
        onboardingFeedbackService.markFeedbackAsRead(feedbackId.value)
}
