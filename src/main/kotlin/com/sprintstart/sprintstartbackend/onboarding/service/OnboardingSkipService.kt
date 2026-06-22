package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSkip
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toReviewResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.UpdateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetAllOnboardingSkipsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.ReviewOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingSkipRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Manages skip requests for onboarding steps.
 *
 * A pending skip request does not complete the step on its own. The step remains
 * waiting until an admin accepts or denies the request. Accepted skips mark the
 * step as skipped and completed; denied skips return it to waiting.
 *
 * The service enforces ownership checks for user-facing methods by resolving the
 * authenticated user through [UserApi] and then restricting step or skip lookups
 * to that user's onboarding path. Skip deletion is handled through orphan removal
 * on the owning [OnboardingStep.skips] collection rather than an explicit repository
 * delete call.
 */
@Service
class OnboardingSkipService(
    private val onboardingSkipRepository: OnboardingSkipRepository,
    private val onboardingStepRepository: OnboardingStepRepository,
    private val userApi: UserApi,
) {
//  ========================== Methods for users ==========================

    /**
     * Returns every skip request belonging to the authenticated user.
     *
     * @param authId External authentication identifier.
     * @return All skips on the user's onboarding path ordered by creation time.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the user does not exist.
     */
    @Transactional(readOnly = true)
    fun getAllSkipsForMe(authId: String): List<GetOnboardingSkipResponse> {
        val userId = getUserId(authId)

        return onboardingSkipRepository
            .findAllByStepPhasePathUserIdOrderByCreatedAtAsc(userId)
            .map { it.toGetResponse() }
    }

    /**
     * Returns all skip requests for one step on the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param stepId Identifier of the step whose skips should be loaded.
     * @return All skips for the requested step ordered by creation time.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the user or step does not exist.
     */
    @Transactional(readOnly = true)
    fun getSkipsByStepIdForMe(authId: String, stepId: UUID): List<GetOnboardingSkipResponse> {
        val userId = getUserId(authId)
        ensureUserOwnsStep(userId, stepId)

        return onboardingSkipRepository
            .findAllByStepIdAndStepPhasePathUserIdOrderByCreatedAtAsc(stepId, userId)
            .map { it.toGetResponse() }
    }

    /**
     * Returns one skip request belonging to the authenticated user.
     *
     * @param authId External authentication identifier.
     * @param skipId Identifier of the skip to return.
     * @return The requested skip.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the user or skip does not exist.
     */
    @Transactional(readOnly = true)
    fun getSkipByIdForMe(authId: String, skipId: UUID): GetOnboardingSkipResponse {
        val userId = getUserId(authId)

        return onboardingSkipRepository
            .findByIdAndStepPhasePathUserId(skipId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No skip found with id: $skipId") }
            .toGetResponse()
    }

    /**
     * Creates a new pending skip request for one step on the authenticated user's onboarding path.
     *
     * The step must still be waiting, and only one pending skip request may exist for the
     * current step at a time.
     *
     * @param authId External authentication identifier.
     * @param stepId Identifier of the step to attach the skip to.
     * @param request Skip creation payload.
     * @return The created skip request.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the user or step does not exist.
     * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST] if the step is not waiting or already has a pending skip.
     */
    @Transactional
    fun createOnboardingSkipForMe(
        authId: String,
        stepId: UUID,
        request: CreateOnboardingSkipRequest,
    ): CreateOnboardingSkipResponse {
        val userId = getUserId(authId)
        val step = ensureUserOwnsStep(userId, stepId)

        ensureStepCanReceivePendingSkip(step)

        val onboardingSkip = OnboardingSkip(
            step = step,
            reason = request.reason,
        )
        step.skips += onboardingSkip

        return onboardingSkipRepository.save(onboardingSkip).toCreateResponse()
    }

    /**
     * Updates the reason of a pending skip request belonging to the authenticated user.
     *
     * @param authId External authentication identifier.
     * @param skipId Identifier of the skip to update.
     * @param request Skip update payload.
     * @return The updated parent step response including the latest skip state.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the user or skip does not exist.
     * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST] if the skip is no longer pending.
     */
    @Transactional
    fun updateOnboardingSkipForMe(
        authId: String,
        skipId: UUID,
        request: UpdateOnboardingSkipRequest,
    ): UpdateOnboardingStepResponse {
        val userId = getUserId(authId)
        val skip = onboardingSkipRepository
            .findByIdAndStepPhasePathUserId(skipId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No skip found with id: $skipId") }

        ensurePending(skip)

        skip.reason = request.reason

        return skip.step.toUpdateResponse()
    }

    /**
     * Deletes one skip request belonging to the authenticated user.
     *
     * Only pending skip requests may be deleted. The skip is removed from the owning
     * step's collection and is expected to be deleted by JPA orphan removal when the
     * transaction is flushed.
     *
     * @param authId External authentication identifier.
     * @param skipId Identifier of the skip to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the user or skip does not exist.
     */
    @Transactional
    fun deleteSkipByIdForMe(authId: String, skipId: UUID) {
        val userId = getUserId(authId)
        val skip = onboardingSkipRepository
            .findByIdAndStepPhasePathUserId(skipId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No skip found with id: $skipId") }

        ensurePending(skip, "deleted")
        deleteSkip(skip)
    }

//  ========================== Methods for admins ==========================

    /**
     * Returns every skip request in the system.
     *
     * @return All skips ordered by creation time.
     */
    @Transactional(readOnly = true)
    fun getAllSkips(): List<GetAllOnboardingSkipsResponse> {
        return onboardingSkipRepository.findAllByOrderByCreatedAtAsc().map { it.toGetAllResponse() }
    }

    /**
     * Returns every skip request belonging to one user.
     *
     * @param userId Identifier of the user whose skips should be loaded.
     * @return All skips on the user's onboarding path ordered by creation time.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the user does not exist.
     */
    @Transactional(readOnly = true)
    fun getAllSkipsByUserId(userId: UUID): List<GetOnboardingSkipResponse> {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: $userId")
        }

        return onboardingSkipRepository
            .findAllByStepPhasePathUserIdOrderByCreatedAtAsc(userId)
            .map { it.toGetResponse() }
    }

    /**
     * Returns every skip request attached to one step.
     *
     * @param stepId Identifier of the step whose skips should be loaded.
     * @return All skips for the requested step ordered by creation time.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the step does not exist.
     */
    @Transactional(readOnly = true)
    fun getAllSkipsByStepId(stepId: UUID): List<GetOnboardingSkipResponse> {
        if (!onboardingStepRepository.existsById(stepId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId")
        }

        return onboardingSkipRepository
            .findAllByStepIdOrderByCreatedAtAsc(stepId)
            .map { it.toGetResponse() }
    }

    /**
     * Returns one skip request by ID.
     *
     * @param skipId Identifier of the skip to return.
     * @return The requested skip.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the skip does not exist.
     */
    @Transactional(readOnly = true)
    fun getSkipById(skipId: UUID): GetOnboardingSkipResponse {
        return onboardingSkipRepository
            .findById(skipId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No skip found with id: $skipId") }
            .toGetResponse()
    }

    /**
     * Accepts one pending skip request and marks the parent step as skipped.
     *
     * Accepting a skip resolves the skip request, stores the admin review comment,
     * sets the step status to [StepStatus.SKIPPED], and records the completion timestamp.
     *
     * @param skipId Identifier of the skip to accept.
     * @param request Admin review payload.
     * @return The resolved skip review response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the skip does not exist.
     * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST] if the skip is no longer pending.
     */
    @Transactional
    fun acceptSkipById(skipId: UUID, request: ReviewOnboardingSkipRequest): ReviewOnboardingSkipResponse {
        val skip = onboardingSkipRepository
            .findById(skipId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No skip found with id: $skipId") }

        ensurePending(skip)

        val reviewedAt = Instant.now()
        skip.status = SkipStatus.ACCEPTED
        skip.reviewComment = request.reviewComment
        skip.resolvedAt = reviewedAt
        skip.step.status = StepStatus.SKIPPED
        skip.step.completedAt = reviewedAt

        return skip.toReviewResponse()
    }

    /**
     * Denies one pending skip request and keeps the parent step waiting.
     *
     * Denying a skip resolves the skip request, stores the admin review comment,
     * clears the parent step completion timestamp, and leaves the step in
     * [StepStatus.WAITING].
     *
     * @param skipId Identifier of the skip to deny.
     * @param request Admin review payload.
     * @return The resolved skip review response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the skip does not exist.
     * @throws ResponseStatusException with [HttpStatus.BAD_REQUEST] if the skip is no longer pending.
     */
    @Transactional
    fun denySkipById(skipId: UUID, request: ReviewOnboardingSkipRequest): ReviewOnboardingSkipResponse {
        val skip = onboardingSkipRepository
            .findById(skipId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No skip found with id: $skipId") }

        ensurePending(skip)

        val reviewedAt = Instant.now()
        skip.status = SkipStatus.DENIED
        skip.reviewComment = request.reviewComment
        skip.resolvedAt = reviewedAt
        skip.step.status = StepStatus.WAITING
        skip.step.completedAt = null

        return skip.toReviewResponse()
    }

    /**
     * Deletes one skip request by ID.
     *
     * Only pending skip requests may be deleted. The skip is removed from the owning
     * step's collection and is expected to be deleted by JPA orphan removal when the
     * transaction is flushed.
     *
     * @param skipId Identifier of the skip to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if the skip does not exist.
     */
    @Transactional
    fun deleteSkipById(skipId: UUID) {
        val skip = onboardingSkipRepository
            .findById(skipId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No skip found with id: $skipId") }

        ensurePending(skip, "deleted")
        deleteSkip(skip)
    }

//  ========================== Helper methods ==========================

    private fun getUserId(authId: String): UUID {
        return userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
    }

    private fun ensureUserOwnsStep(userId: UUID, stepId: UUID): OnboardingStep {
        return onboardingStepRepository
            .findByIdAndPhasePathUserId(stepId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }
    }

    private fun ensureStepCanReceivePendingSkip(step: OnboardingStep) {
        if (step.status != StepStatus.WAITING) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only waiting steps can receive a skip request",
            )
        }

        if (step.skips.lastOrNull()?.status == SkipStatus.PENDING) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Step ${step.id} already has a pending skip request",
            )
        }
    }

    private fun ensurePending(skip: OnboardingSkip, action: String = "modified") {
        if (skip.status != SkipStatus.PENDING) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only pending skips can be $action",
            )
        }
    }

    private fun deleteSkip(skip: OnboardingSkip) {
        val step = skip.step
        step.skips.removeIf { existingSkip -> existingSkip.id == skip.id }
    }
}
