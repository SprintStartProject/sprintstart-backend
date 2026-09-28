package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface KnowledgeRequestRepository : JpaRepository<KnowledgeRequest, UUID> {
    /** The PM inbox for a project: the open queue, oldest first (longest-waiting worst). */
    fun findAllByProjectIdAndStatusOrderByCreatedAtAsc(
        projectId: UUID,
        status: KnowledgeRequestStatus,
    ): List<KnowledgeRequest>

    /**
     * How many questions on a project are still waiting on a person.
     *
     * Counted in the database rather than by reading the queue and taking its length: the badge in
     * the sidebar asks this on every navigation, and the full read now resolves each asker's name
     * and onboarding position -- work nobody needs in order to render a number.
     */
    fun countByProjectIdAndStatus(projectId: UUID, status: KnowledgeRequestStatus): Long

    /** A hire's own escalations, newest first, so they can see what they asked and what came back. */
    fun findAllByHireIdOrderByCreatedAtDesc(hireId: UUID): List<KnowledgeRequest>

    /** Every escalation a hire made on a project — the surviving "needed a person" signal. */
    fun findAllByHireIdAndProjectId(hireId: UUID, projectId: UUID): List<KnowledgeRequest>

    /**
     * Closes a request as answered against [canonicalAnswerId], only while it is still [open] on
     * [projectId].
     *
     * One statement, so two people answering — or one answering while another dismisses — cannot both
     * succeed: whichever runs second finds nothing still open.
     *
     * @return 1 when this call closed the request, 0 when it was not open on that project.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """UPDATE KnowledgeRequest r
           SET r.status = :answered, r.answeredBy = :answeredBy, r.answeredAt = :answeredAt,
               r.canonicalAnswerId = :canonicalAnswerId
           WHERE r.id = :id AND r.projectId = :projectId AND r.status = :open""",
    )
    @Suppress("LongParameterList") // One column per parameter; a carrier type would only rename them.
    fun answerIfOpen(
        id: UUID,
        projectId: UUID,
        open: KnowledgeRequestStatus,
        answered: KnowledgeRequestStatus,
        answeredBy: UUID,
        answeredAt: Instant,
        canonicalAnswerId: UUID,
    ): Int

    /**
     * Dismisses a request only while it is still [open] on [projectId], for the same reason as
     * [answerIfOpen].
     *
     * @return 1 when this call dismissed the request, 0 when it was not open on that project.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """UPDATE KnowledgeRequest r
           SET r.status = :dismissed
           WHERE r.id = :id AND r.projectId = :projectId AND r.status = :open""",
    )
    fun dismissIfOpen(
        id: UUID,
        projectId: UUID,
        open: KnowledgeRequestStatus,
        dismissed: KnowledgeRequestStatus,
    ): Int

    fun deleteAllByHireId(hireId: UUID)
}
