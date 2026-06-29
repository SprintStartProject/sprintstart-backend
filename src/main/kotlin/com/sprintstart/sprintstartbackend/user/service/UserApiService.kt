package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.UserOnboardingProfile
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

/**
 * Service implementation of the user API used by other modules.
 *
 * Provides a small module-facing adapter over the user repository without exposing
 * controller DTOs or internal user service workflows.
 */
@Service
class UserApiService(
    private val userRepository: UserRepository,
) : UserApi {
    /**
     * Checks whether a user with the given identifier exists.
     *
     * @param id The unique identifier of the user to check.
     * @return `true` if a user with the given identifier exists, otherwise `false`.
     */
    @Transactional(readOnly = true)
    override fun exists(id: UUID): Boolean {
        return userRepository.existsById(id)
    }

    /**
     * Resolves the internal user ID for an external authentication identifier.
     *
     * @param authId External authentication identifier.
     * @return The matching user ID when present.
     */
    @Transactional
    override fun getUserIdByAuthId(authId: String): Optional<UUID> {
        return userRepository.findIdByAuthId(authId)
    }

    /**
     * Returns the onboarding-relevant profile for a user identified by auth ID.
     *
     * @param authId External authentication identifier.
     * @return The user's onboarding profile when present.
     */
    @Transactional(readOnly = true)
    override fun getOnboardingProfileByAuthId(authId: String): Optional<UserOnboardingProfile> =
        userRepository.findByAuthId(authId).map { user ->
            UserOnboardingProfile(
                id = user.id,
                workingArea = user.workingArea,
            )
        }
}
