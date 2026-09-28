package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.FaqQuestion
import com.sprintstart.sprintstartbackend.insights.repository.projection.FaqGroupQuestionCount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface FaqQuestionRepository : JpaRepository<FaqQuestion, UUID> {
    /**
     * Whether a chat message has already been filed into the FAQ.
     *
     * The live classification runs off an event, and an event can be redelivered — without this a
     * retry would count the same question a second time and inflate the group it lands in.
     */
    fun existsBySourceMessageId(sourceMessageId: UUID): Boolean

    /**
     * Counts each group's questions asked within `[from, to)`.
     *
     * Aggregated in the database rather than by walking every group's questions: a project's
     * question rows grow without bound, while the result is one row per group.
     *
     * @return counts for groups with at least one question in the window; groups with none are
     * absent rather than present with zero.
     */
    @Query(
        """
        select new com.sprintstart.sprintstartbackend.insights.repository.projection.FaqGroupQuestionCount(
            q.group.id, count(q)
        )
        from FaqQuestion q
        where q.group.projectId = :projectId and q.askedAt >= :from and q.askedAt < :to
        group by q.group.id
        """,
    )
    fun countPerGroupAskedBetween(
        @Param("projectId") projectId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<FaqGroupQuestionCount>
}
