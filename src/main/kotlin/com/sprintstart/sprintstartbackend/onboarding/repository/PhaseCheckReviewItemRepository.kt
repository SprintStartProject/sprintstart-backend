package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckReviewItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface PhaseCheckReviewItemRepository : JpaRepository<PhaseCheckReviewItem, UUID> {
    /** Open (not yet resolved) carried-over questions the user must re-answer in [targetPhaseId]. */
    fun findAllByUserIdAndTargetPhaseIdAndResolvedFalseOrderByCreatedAtAsc(
        userId: UUID,
        targetPhaseId: UUID,
    ): MutableList<PhaseCheckReviewItem>

    /** All still-open carried-over items for a user and question, to avoid duplicate carries. */
    fun findAllByUserIdAndQuestionIdAndResolvedFalse(userId: UUID, questionId: UUID): MutableList<PhaseCheckReviewItem>
}
