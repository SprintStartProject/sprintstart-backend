package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toAdminGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toReadResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.feedback.CreateOnboardingFeedbackRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.ReadOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class OnboardingFeedbackService(
    private val onboardingFeedbackRepository: OnboardingFeedbackRepository,
    private val onboardingStepRepository: OnboardingStepRepository,
    private val userApi: UserApi,
) {
//  ========================== Methods for users ==========================

    /**
     * Retrieves all feedback provided by this user.
     *
     * @param authId The user id.
     * @throws ResponseStatusException (not found) If no user with the given id could be found
     */
    @Transactional(readOnly = true)
    fun getAllFeedbackForMe(authId: String): List<GetOnboardingFeedbackResponse> {
        val userId = getUserId(authId)

        return onboardingFeedbackRepository
            .findAllByUserIdOrderByCreatedAtAsc(userId)
            .map { it.toGetResponse() }
    }

    /**
     * Retrieves all feedback by this user for a given onboarding path step.
     *
     * @param authId The id of the user to filter feedback for.
     * @param stepId The id of the step to retrieve feedback for.
     * @throws ResponseStatusException (not found) if user or step with given id could not be found.
     */
    @Transactional(readOnly = true)
    fun getFeedbackByStepIdForMe(authId: String, stepId: UUID): List<GetOnboardingFeedbackResponse> {
        val userId = getUserId(authId)
        ensureUserOwnsStep(userId, stepId)

        return onboardingFeedbackRepository
            .findAllByStepIdAndUserIdOrderByCreatedAtAsc(stepId, userId)
            .map { it.toGetResponse() }
    }

    /**
     * Adds new feedback for a given user on a onboarding path step.
     *
     * @param authId The id of the user which wants to add this feedback
     * @param request [CreateOnboardingFeedbackRequest] The feedback information.
     * @throws ResponseStatusException (not found) if user or step with given id could not be found.
     */
    @Transactional
    fun createFeedbackForMe(authId: String, request: CreateOnboardingFeedbackRequest): GetOnboardingFeedbackResponse {
        val userId = getUserId(authId)
        val step = request.stepId?.let { stepId -> ensureUserOwnsStep(userId, stepId) }

        val feedback = OnboardingFeedback(
            userId = userId,
            step = step,
            helpful = request.helpful,
            message = request.message,
        )

        // Persist exactly once: via the owning step's cascade when a step is set,
        // otherwise directly. Doing both (collection add + save) attaches two
        // instances with the same assigned UUID and throws NonUniqueObjectException.
        if (step != null) {
            step.feedback.add(feedback)
        } else {
            onboardingFeedbackRepository.save(feedback)
        }

        return feedback.toGetResponse()
    }

//  ========================== Methods for admins ==========================

    /**
     * Retrieves all feedback for all users.
     */
    @Transactional(readOnly = true)
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
    fun getAllFeedbackByUserId(userId: UUID): List<GetAdminOnboardingFeedbackResponse> {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId")
        }

        return onboardingFeedbackRepository
            .findAllByUserIdOrderByCreatedAtAsc(userId)
            .map { it.toAdminGetResponse() }
    }

    /**
     * Retrieves all feedbacks provided for a specific step.
     *
     * @param stepId The id of the step to query feedbacks for.
     * @throws ResponseStatusException (not found) if step with given id could not be found.
     */
    @Transactional(readOnly = true)
    fun getAllFeedbackByStepId(stepId: UUID): List<GetAdminOnboardingFeedbackResponse> {
        if (!onboardingStepRepository.existsById(stepId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId")
        }

        return onboardingFeedbackRepository
            .findAllByStepIdOrderByCreatedAtAsc(stepId)
            .map { it.toAdminGetResponse() }
    }

    /**
     * Marks specific feedback as read by an admin.
     *
     * @param feedbackId The id of the feedback to mark as read.
     * @throws ResponseStatusException (not found) if feedback with given id could not be found.
     */
    @Transactional(readOnly = true)
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
     * Checks if a given step is owned by a specific user.
     *
     * @param userId The id of the owning user.
     * @param stepId The id of the owned step.
     * @throws ResponseStatusException (not found) if the step couldn't be found.
     */
    private fun ensureUserOwnsStep(userId: UUID, stepId: UUID) =
        onboardingStepRepository
            .findByIdAndPhasePathUserId(stepId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }
}
