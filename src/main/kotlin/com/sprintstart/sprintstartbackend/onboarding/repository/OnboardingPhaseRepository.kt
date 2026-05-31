package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OnboardingPhaseRepository : JpaRepository<OnboardingPhase, UUID>
