package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckReviewItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface PhaseCheckReviewItemRepository : JpaRepository<PhaseCheckReviewItem, UUID> {
    /** The user's open (not yet resolved) review pool, oldest first. */
    fun findAllByUserIdAndResolvedFalseOrderByCreatedAtAsc(userId: UUID): MutableList<PhaseCheckReviewItem>

    /** How many questions the user still has to answer correctly; gates onboarding completion. */
    fun countByUserIdAndResolvedFalse(userId: UUID): Long

    /**
     * Whether a question ever entered this user's review pool, regardless of whether it was
     * already resolved.
     *
     * Used to keep a question from being collected twice: a resolved item must not be
     * recreated when the user retakes the phase check it came from, because the underlying
     * attempt history still counts that question as once-wrong forever.
     */
    fun existsByUserIdAndQuestionId(userId: UUID, questionId: UUID): Boolean
}
