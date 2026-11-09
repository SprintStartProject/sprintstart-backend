package com.sprintstart.sprintstartbackend.path.repository

import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingFeedback
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OnboardingFeedbackRepository : JpaRepository<OnboardingFeedback, UUID> {
    fun findAllByOrderByCreatedAtAsc(): MutableList<OnboardingFeedback>

    fun findAllByUserIdOrderByCreatedAtAsc(userId: UUID): MutableList<OnboardingFeedback>

    fun findAllByStepIdOrderByCreatedAtAsc(stepId: UUID): MutableList<OnboardingFeedback>

    fun findAllByStepIdAndUserIdOrderByCreatedAtAsc(stepId: UUID, userId: UUID): MutableList<OnboardingFeedback>

    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<OnboardingFeedback>

    /** Deletes every piece of feedback the user gave, on a step or not. */
    fun deleteAllByUserId(userId: UUID)
}
