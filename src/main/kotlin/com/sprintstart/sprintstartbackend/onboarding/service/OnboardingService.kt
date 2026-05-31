package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.GetAllOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class OnboardingService(
    private val onboardingPathRepository: OnboardingPathRepository,
) {
    fun getAllOnboardingPaths(): List<GetAllOnboardingPathResponse> {
        return onboardingPathRepository.findAll().map {
            it.toGetAllResponse()
        }
    }

    fun getOnboardingPathByUserId(userId: UUID): GetOnboardingPathResponse {
        return onboardingPathRepository.findOnboardingPathByUserId(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No path found for user with id: $userId") }
            .toGetResponse()
    }

    fun deleteOnboardingPathById(pathId: UUID) {
        onboardingPathRepository.deleteById(pathId)
    }
}
