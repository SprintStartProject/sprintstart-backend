package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OnboardingPathRepository : JpaRepository<OnboardingPath, UUID> {
    fun findOnboardingPathByUserId(userId: UUID): Optional<OnboardingPath>

    fun deleteByUserId(userId: UUID)

    fun existsByUserId(userId: UUID): Boolean
}
