package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Provides onboarding path read and delete operations.
 *
 * User-scoped operations resolve the current user through [UserApi] before accessing
 * the owning onboarding path. Admin-scoped operations address a user directly by UUID.
 */
@Service
class OnboardingPathService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val userApi: UserApi,
) {
//  ========================== Methods for users ==========================

    /**
     * Returns the onboarding path for the authenticated user.
     *
     * The user is resolved from the external auth ID before the path lookup is performed.
     *
     * @param authId External authentication identifier.
     * @return The authenticated user's onboarding path.
     * @throws ResponseStatusException When the user or onboarding path does not exist.
     */
    @Transactional(readOnly = true)
    fun getOnboardingPathForMe(authId: String): GetOnboardingPathForUserResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

        return onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found for user with id: $userId") }
            .toGetForUserResponse()
    }

    /**
     * Deletes the onboarding path owned by the authenticated user.
     *
     * @param authId External authentication identifier.
     * @throws ResponseStatusException When the user does not exist.
     */
    fun deleteOnboardingPathForMe(authId: String) {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

        onboardingPathRepository.deleteByUserId(userId)
    }

//  ========================== Methods for admins ==========================

    /**
     * Returns the onboarding path for a specific user.
     *
     * The target user must exist before the path lookup is attempted.
     *
     * @param userId Identifier of the user whose path should be loaded.
     * @return The user's onboarding path.
     * @throws ResponseStatusException When the user or onboarding path does not exist.
     */
    fun getOnboardingPathByUserId(userId: UUID): GetOnboardingPathResponse {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }

        return onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No onboarding path found with for: $userId") }
            .toGetResponse()
    }

    /**
     * Deletes the onboarding path associated with a specific user.
     *
     * @param userId The ID of the user whose path should be deleted.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no user exists with [userId].
     */
    fun deleteOnboardingPathByUserId(userId: UUID) {
        if (!userApi.exists(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with id: $userId")
        }
        onboardingPathRepository.deleteByUserId(userId)
    }
}
