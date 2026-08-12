package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.KnowledgeRequest
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeRequestRepository : JpaRepository<KnowledgeRequest, UUID> {
    /** The PM inbox for a project: the open queue, oldest first (longest-waiting worst). */
    fun findAllByProjectIdAndStatusOrderByCreatedAtAsc(
        projectId: UUID,
        status: KnowledgeRequestStatus,
    ): List<KnowledgeRequest>

    /** A hire's own escalations, newest first, so they can see what they asked and what came back. */
    fun findAllByHireIdOrderByCreatedAtDesc(hireId: UUID): List<KnowledgeRequest>

    /** Every escalation a hire made on a project — the surviving "needed a person" signal. */
    fun findAllByHireIdAndProjectId(hireId: UUID, projectId: UUID): List<KnowledgeRequest>
}
