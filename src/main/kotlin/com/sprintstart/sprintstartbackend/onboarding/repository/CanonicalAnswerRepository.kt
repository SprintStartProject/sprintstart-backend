package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface CanonicalAnswerRepository : JpaRepository<CanonicalAnswer, UUID> {
    /** Every canonical answer on a project, for the PM to manage. */
    fun findAllByProjectIdOrderByUpdatedAtDesc(projectId: UUID): List<CanonicalAnswer>

    /** The answer pool the buddy's canonical-answer tool searches, across the caller's projects. */
    fun findAllByProjectIdIn(projectIds: Collection<UUID>): List<CanonicalAnswer>

    /**
     * Rewords an answer on [projectId] only while it is exactly as it was when [seenUpdatedAt] was read.
     *
     * One statement, so an edit confirmed from a preview cannot overwrite a change somebody else made
     * after that preview was written.
     *
     * @return 1 when this call applied the edit, 0 when the answer changed since, or is not on that project.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """UPDATE CanonicalAnswer a
           SET a.question = :question, a.answer = :answer, a.authorId = :authorId, a.updatedAt = :updatedAt
           WHERE a.id = :id AND a.projectId = :projectId AND a.updatedAt = :seenUpdatedAt""",
    )
    @Suppress("LongParameterList") // One column per parameter; a carrier type would only rename them.
    fun editIfUnchanged(
        id: UUID,
        projectId: UUID,
        question: String,
        answer: String,
        authorId: UUID,
        updatedAt: Instant,
        seenUpdatedAt: Instant,
    ): Int
}
