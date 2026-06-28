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

    @Transactional(readOnly = true)
    fun getAllFeedbackForMe(authId: String): List<GetOnboardingFeedbackResponse> {
        val userId = getUserId(authId)

        return onboardingFeedbackRepository
            .findAllByUserIdOrderByCreatedAtAsc(userId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getFeedbackByStepIdForMe(authId: String, stepId: UUID): List<GetOnboardingFeedbackResponse> {
        val userId = getUserId(authId)
        ensureUserOwnsStep(userId, stepId)

        return onboardingFeedbackRepository
            .findAllByStepIdAndUserIdOrderByCreatedAtAsc(stepId, userId)
            .map { it.toGetResponse() }
    }

    @Transactional
    fun createFeedbackForMe(authId: String, request: CreateOnboardingFeedbackRequest): GetOnboardingFeedbackResponse {
        if (request.message.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback message must not be blank")
        }

        val userId = getUserId(authId)
        val step = request.stepId?.let { stepId -> ensureUserOwnsStep(userId, stepId) }

        val feedback = OnboardingFeedback(
            userId = userId,
            step = step,
            helpful = request.helpful,
            message = request.message,
        )
        step?.feedback?.add(feedback)

        return onboardingFeedbackRepository.save(feedback).toGetResponse()
    }

//  ========================== Methods for admins ==========================

    @Transactional(readOnly = true)
    fun getAllFeedback(): List<GetAdminOnboardingFeedbackResponse> {
        return onboardingFeedbackRepository
            .findAllByOrderByCreatedAtAsc()
            .map { it.toAdminGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getAllFeedbackByUserId(userId: UUID): List<GetAdminOnboardingFeedbackResponse> {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId")
        }

        return onboardingFeedbackRepository
            .findAllByUserIdOrderByCreatedAtAsc(userId)
            .map { it.toAdminGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getAllFeedbackByStepId(stepId: UUID): List<GetAdminOnboardingFeedbackResponse> {
        if (!onboardingStepRepository.existsById(stepId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId")
        }

        return onboardingFeedbackRepository
            .findAllByStepIdOrderByCreatedAtAsc(stepId)
            .map { it.toAdminGetResponse() }
    }

    @Transactional
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

    private fun getUserId(authId: String): UUID {
        return userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
    }

    private fun ensureUserOwnsStep(userId: UUID, stepId: UUID) =
        onboardingStepRepository
            .findByIdAndPhasePathUserId(stepId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }
}
