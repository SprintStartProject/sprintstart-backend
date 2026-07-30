package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckReviewItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface PhaseCheckReviewItemRepository : JpaRepository<PhaseCheckReviewItem, UUID> {
    /** The user's open (not yet resolved) review pool, oldest first. */
    fun findAllByUserIdAndResolvedFalseOrderByCreatedAtAsc(userId: UUID): MutableList<PhaseCheckReviewItem>

    /**
     * How many questions the user still has to answer correctly; gates onboarding completion.
     *
     * Items whose question no longer exists are excluded, because they can never be answered
     * and would block completion forever. [questionId] is a plain UUID with no foreign key, so
     * such items outlive their question whenever a check is edited or a phase is deleted —
     * counting them would also disagree with the review pool listing, which skips them too.
     */
    @Query(
        """
        SELECT COUNT(item) FROM PhaseCheckReviewItem item
        WHERE item.userId = :userId
          AND item.resolved = false
          AND EXISTS (SELECT 1 FROM PhaseCheckQuestion question WHERE question.id = item.questionId)
        """,
    )
    fun countOpenAnswerableByUserId(@Param("userId") userId: UUID): Long

    /**
     * Every review item referencing one of the given questions, across all users.
     *
     * Used to clean up after questions are deleted: the callers need the affected users to
     * re-evaluate their onboarding completion, not just the rows to remove.
     */
    fun findAllByQuestionIdIn(questionIds: Collection<UUID>): List<PhaseCheckReviewItem>

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
