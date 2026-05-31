package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.GetAllOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.GetOnboardingPathResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController(
    val onboardingService: OnboardingService,
) {
    // Get: All paths
    @GetMapping("/paths")
    fun getAllPaths(): List<GetAllOnboardingPathResponse> {
        return onboardingService.getAllOnboardingPaths()
    }

    // Get: Path for user
    @GetMapping("/paths/for-user/{userId}")
    fun getPathForUser(@PathVariable userId: UUID): GetOnboardingPathResponse {
        return onboardingService.getOnboardingPathByUserId(userId)
    }

    // Delete: path by pathId
    @DeleteMapping("/paths/{pathId}")
    fun deletePath(@PathVariable pathId: UUID) {
        onboardingService.deleteOnboardingPathById(pathId)
    }

    // Todo: - endpoints for phase
    //       - endpoints for step
    //       - endpoints for task
    //       - endpoints for resource
}
