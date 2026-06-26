package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.OnboardingPathRequest
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toEntities
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.path.OnboardingSseEvent
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.service.UserService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingPersonalizationService(
    private val onboardingAiClient: OnboardingAiClient,
    private val onboardingPathRepository: OnboardingPathRepository,
    private val userService: UserService,
) {
    @Transactional
    fun personalize(authId: String): Flow<OnboardingSseEvent> {
        val user = userService.getMe(authId)
        val workingArea = user.workingArea.toAiScope()
        val experience = user.experience

        onboardingPathRepository.deleteByUserId(user.id)

        val request = OnboardingPathRequest(
            workingArea = workingArea,
            experience = experience,
        )

        return onboardingAiClient
            .generatePath(request)
            .map { event ->
                when (event.type) {
                    "stage" -> OnboardingSseEvent(
                        type = "stage",
                        name = event.name,
                        detail = event.detail,
                    )

                    "path" -> {
                        val savedPath = event.path?.let { aiPath ->
                            val entity = aiPath.toEntities(user.id)
                            onboardingPathRepository.save(entity)
                            entity.toGetForUserResponse()
                        }
                        OnboardingSseEvent(
                            type = "path",
                            path = savedPath,
                        )
                    }

                    "done" -> OnboardingSseEvent(type = "done")

                    "error" -> OnboardingSseEvent(
                        type = "error",
                        message = event.message,
                    )

                    else -> OnboardingSseEvent(type = event.type)
                }
            }
    }
}
