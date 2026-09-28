package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.QuestionAttempt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
interface QuestionAttemptRepository : JpaRepository<QuestionAttempt, UUID> {
    fun findAllByQuestionIdAndUserIdOrderByCreatedAtDesc(questionId: UUID, userId: UUID): MutableList<QuestionAttempt>

    /** Whether the user ever answered this question correctly. */
    fun existsByQuestionIdAndUserIdAndCorrectTrue(questionId: UUID, userId: UUID): Boolean

    /** IDs of every question the user ever answered correctly. */
    @Query("SELECT DISTINCT a.questionId FROM QuestionAttempt a WHERE a.userId = :userId AND a.correct = true")
    fun findPassedQuestionIdsByUserId(@Param("userId") userId: UUID): List<UUID>

    /** IDs of every question the user ever attempted (correctly or not). */
    @Query("SELECT DISTINCT a.questionId FROM QuestionAttempt a WHERE a.userId = :userId")
    fun findAttemptedQuestionIdsByUserId(@Param("userId") userId: UUID): List<UUID>
}
