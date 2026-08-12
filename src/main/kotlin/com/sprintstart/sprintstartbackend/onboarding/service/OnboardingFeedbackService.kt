package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toAdminGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toReadResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.feedback.CreateOnboardingFeedbackRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.ReadOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.ModulePageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class OnboardingFeedbackService(
    private val onboardingFeedbackRepository: OnboardingFeedbackRepository,
    private val modulePageRepository: ModulePageRepository,
    private val userApi: UserApi,
    private val contentQualityService: ContentQualityService,
) {
//  ========================== Methods for users ==========================

    /**
     * Retrieves all feedback provided by this user.
     *
     * @param authId The user id.
     * @throws ResponseStatusException (not found) If no user with the given id could be found
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all feedback for this user")
    fun getAllFeedbackForMe(authId: String): List<GetOnboardingFeedbackResponse> {
        val userId = getUserId(authId)

        return onboardingFeedbackRepository
            .findAllByUserIdOrderByCreatedAtAsc(userId)
            .map { it.toGetResponse() }
    }

    /**
     * Retrieves this user's feedback on one module page.
     *
     * @param authId The id of the user to filter feedback for.
     * @param pageId The module page to retrieve feedback for.
     * @throws ResponseStatusException (not found) if the user or page could not be found.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all feedback this user left on a specific module page")
    fun getFeedbackByPageIdForMe(authId: String, pageId: UUID): List<GetOnboardingFeedbackResponse> {
        val userId = getUserId(authId)
        findPage(pageId)

        return onboardingFeedbackRepository
            .findAllByPageIdAndUserIdOrderByCreatedAtAsc(pageId, userId)
            .map { it.toGetResponse() }
    }

    /**
     * Records this user's feedback, optionally about one module page.
     *
     * The page is shared, so "this didn't help" accumulates across every hire who read it -- which
     * is what makes the content-quality loop worth running at all.
     *
     * @param authId The id of the user leaving the feedback.
     * @param request [CreateOnboardingFeedbackRequest] The feedback information.
     * @throws ResponseStatusException (not found) if the user or page could not be found.
     */
    @Transactional
    @Tracked("Adding feedback for this user")
    fun createFeedbackForMe(authId: String, request: CreateOnboardingFeedbackRequest): GetOnboardingFeedbackResponse {
        val userId = getUserId(authId)
        val page = request.pageId?.let { findPage(it) }

        val feedback = onboardingFeedbackRepository.save(
            OnboardingFeedback(
                userId = userId,
                page = page,
                helpful = request.helpful,
                message = request.message,
            ),
        )

        if (page != null && feedback.helpful == false) {
            contentQualityService.checkAndTriggerRegeneration(page)
        }

        return feedback.toGetResponse()
    }

//  ========================== Methods for admins ==========================

    /**
     * Retrieves all feedback for all users.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all feedback of all users")
    fun getAllFeedback(): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackRepository
            .findAllByOrderByCreatedAtAsc()
            .map { it.toAdminGetResponse() }
    }

    /**
     * Retrieves all feedbacks provided by a given user.
     *
     * @param userId the id of the user to get feedbacks of.
     * @throws ResponseStatusException (not found) if user or step with given id could not be found.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all feedback of a specific user")
    fun getAllFeedbackByUserId(userId: UUID): List<GetAdminOnboardingFeedbackResponse> {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId")
        }

        return onboardingFeedbackRepository
            .findAllByUserIdOrderByCreatedAtAsc(userId)
            .map { it.toAdminGetResponse() }
    }

    /**
     * Retrieves everybody's feedback on one module page.
     *
     * @param pageId The module page to query feedback for.
     * @throws ResponseStatusException (not found) if no page with that id exists.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all feedback for a specific module page")
    fun getAllFeedbackByPageId(pageId: UUID): List<GetAdminOnboardingFeedbackResponse> {
        if (!modulePageRepository.existsById(pageId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No module page found with id: $pageId")
        }

        return onboardingFeedbackRepository
            .findAllByPageIdOrderByCreatedAtAsc(pageId)
            .map { it.toAdminGetResponse() }
    }

    /**
     * Marks specific feedback as read by an admin.
     *
     * @param feedbackId The id of the feedback to mark as read.
     * @throws ResponseStatusException (not found) if feedback with given id could not be found.
     */
    @Transactional
    @Tracked("Marking feedback as read")
    fun markFeedbackAsRead(feedbackId: UUID): ReadOnboardingFeedbackResponse {
        val feedback = onboardingFeedbackRepository
            .findById(feedbackId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "No feedback found with id: $feedbackId")
            }

        feedback.read = true

        return feedback.toReadResponse()
    }

//  ========================== Helper methods ==========================

    /**
     * Retrieves a user's id by it's auth id.
     *
     * @param authId The auth id used to query.
     * @throws ResponseStatusException (not found) if the user couldn't be found.
     */
    private fun getUserId(authId: String): UUID {
        return userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
    }

    /**
     * Resolves a module page. There is no per-user ownership check to make: the page is shared, so
     * anyone who can read the module can say whether it helped.
     *
     * @throws ResponseStatusException (not found) if no page with that id exists.
     */
    private fun findPage(pageId: UUID): ModulePage =
        modulePageRepository
            .findById(pageId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No module page found with id: $pageId") }
}
