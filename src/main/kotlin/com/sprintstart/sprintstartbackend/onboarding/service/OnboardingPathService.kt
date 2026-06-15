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

@Service
class OnboardingPathService(
    private val onboardingPathRepository: OnboardingPathRepository,
    private val userApi: UserApi,
) {
//  ========================== Methods for users ==========================

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

    fun deleteOnboardingPathForMe(authId: String) {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

        onboardingPathRepository.deleteByUserId(userId)
    }

//  ========================== Methods for admins ==========================

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

// TODO: add doc
