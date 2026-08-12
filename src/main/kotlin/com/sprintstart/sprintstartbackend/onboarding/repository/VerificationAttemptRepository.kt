package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.VerificationAttempt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VerificationAttemptRepository : JpaRepository<VerificationAttempt, UUID> {
    fun findAllByVerificationIdAndUserIdOrderByAttemptNoDesc(
        verificationId: UUID,
        userId: UUID,
    ): List<VerificationAttempt>

    fun countByVerificationIdAndUserId(verificationId: UUID, userId: UUID): Int

    /**
     * Whether someone else has already passed this verification with the same answer.
     *
     * Used by `ARTIFACT` grading, where the answer is a pull request number: one pull request
     * cannot be evidence that two different people did the work. Scoped to a single verification,
     * so the answer refers to the same task on the same linked repository.
     */
    fun existsByVerificationIdAndAnswerAndPassedIsTrueAndUserIdNot(
        verificationId: UUID,
        answer: String,
        userId: UUID,
    ): Boolean
}
