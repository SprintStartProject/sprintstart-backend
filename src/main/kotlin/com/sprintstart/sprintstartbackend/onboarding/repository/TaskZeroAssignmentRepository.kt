package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskZeroAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TaskZeroAssignmentRepository : JpaRepository<TaskZeroAssignment, UUID> {
    fun findByHireIdAndProjectId(hireId: UUID, projectId: UUID): TaskZeroAssignment?

    fun deleteByHireIdAndProjectId(hireId: UUID, projectId: UUID)

    /** Proposal ids already handed to a hire — so the same piece of work is never assigned twice. */
    @Query("SELECT a.proposalId FROM TaskZeroAssignment a")
    fun findAllAssignedProposalIds(): List<UUID>
}
