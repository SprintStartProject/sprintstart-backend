package com.sprintstart.sprintstartbackend.path.external

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
import java.util.UUID

/**
 * A team member's path as their manager reviews it: what is on it, what they asked to skip, what they
 * said about it, and starting it over.
 *
 * By id and without an access check: the caller decides first whether the manager may see this member,
 * through [element] and [PathElement.ownerId]. A call that names something that is not there throws a 404
 * [org.springframework.web.server.ResponseStatusException].
 */
interface PathReviewApi {
    /** The member's path as they see it. */
    fun pathOf(userId: UserId): GetOnboardingPathForUserResponse

    /** How much is on the member's path and how far they got, or null when they have none. */
    fun summaryOf(userId: UserId): PathSummary?

    /** What [id] points at as a [kind], and whose path it is on; null when it is not there. */
    fun element(kind: PathElementKind, id: UUID): PathElement?

    /** Deletes the member's path; it is rebuilt from the blueprint the next time they open it. */
    fun resetPath(userId: UserId)

    /** Every skip request the member made. */
    fun skipsOf(userId: UserId): List<GetOnboardingSkipResponse>

    /** Skip request [skipId]. */
    fun skip(skipId: SkipId): GetOnboardingSkipResponse

    /** Grants skip request [skipId]. */
    fun acceptSkip(skipId: SkipId, request: ReviewOnboardingSkipRequest): ReviewOnboardingSkipResponse

    /** Turns skip request [skipId] down. */
    fun denySkip(skipId: SkipId, request: ReviewOnboardingSkipRequest): ReviewOnboardingSkipResponse

    /** Deletes skip request [skipId]. */
    fun deleteSkip(skipId: SkipId)

    /** Everything the member said about their onboarding. */
    fun feedbackOf(userId: UserId): List<GetAdminOnboardingFeedbackResponse>

    /** Marks feedback [feedbackId] read. */
    fun markFeedbackRead(feedbackId: FeedbackId): ReadOnboardingFeedbackResponse
}
