package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The caller's onboarding profile, provided they are assigned to [projectId] -- the check every
 * project-scoped onboarding entry point makes before it says anything about that project.
 *
 * @throws ResponseStatusException 404 for an unknown user, 403 when not assigned to the project.
 */
fun UserApi.onboardingProfileInProject(authId: String, projectId: UUID): UserOnboardingProfile {
    val profile = getOnboardingProfileByAuthId(authId)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId: $authId not found") }
    if (projectId !in profile.projectIds) {
        throw ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "User with authId: $authId is not assigned to project: $projectId",
        )
    }
    return profile
}
