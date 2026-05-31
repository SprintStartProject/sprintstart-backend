package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OnboardingResourceRepository : JpaRepository<OnboardingStep, UUID>
