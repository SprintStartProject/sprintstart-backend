package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

interface OnboardingPathRepository : JpaRepository<OnboardingPath, UUID> {
    fun findOnboardingPathByUserId(userId: UUID): Optional<OnboardingPath>

    /**
     * Deletes a user's path with everything under it.
     *
     * Transactional here rather than left to each caller: a derived delete throws
     * `TransactionRequiredException` when it runs with no transaction, and the services that call it
     * are not themselves transactional.
     */
    @Transactional
    fun deleteByUserId(userId: UUID)

    fun existsByUserId(userId: UUID): Boolean

    fun findByUserId(userId: UUID): Optional<OnboardingPath>

    fun findByUserIdIn(userIds: Collection<UUID>): List<OnboardingPath>
}
