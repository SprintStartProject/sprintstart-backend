package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSkip
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OnboardingSkipRepository : JpaRepository<OnboardingSkip, UUID> {
    fun findAllByOrderByCreatedAtAsc(): MutableList<OnboardingSkip>

    fun findAllByStepPhasePathUserIdOrderByCreatedAtAsc(stepPhasePathUserId: UUID): MutableList<OnboardingSkip>

    fun findAllByStepIdOrderByCreatedAtAsc(stepId: UUID): MutableList<OnboardingSkip>

    fun findAllByStepIdAndStepPhasePathUserIdOrderByCreatedAtAsc(
        stepId: UUID,
        stepPhasePathUserId: UUID,
    ): MutableList<OnboardingSkip>

    fun findByIdAndStepPhasePathUserId(id: UUID, stepPhasePathUserId: UUID): Optional<OnboardingSkip>
}
