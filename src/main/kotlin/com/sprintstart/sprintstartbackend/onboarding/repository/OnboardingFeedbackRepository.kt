package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OnboardingFeedbackRepository : JpaRepository<OnboardingFeedback, UUID> {
    fun findAllByOrderByCreatedAtAsc(): MutableList<OnboardingFeedback>

    fun findAllByUserIdOrderByCreatedAtAsc(userId: UUID): MutableList<OnboardingFeedback>

    fun findAllByStepIdOrderByCreatedAtAsc(stepId: UUID): MutableList<OnboardingFeedback>

    fun findAllByStepIdAndUserIdOrderByCreatedAtAsc(stepId: UUID, userId: UUID): MutableList<OnboardingFeedback>

    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<OnboardingFeedback>
}
