package com.sprintstart.sprintstartbackend.starterwork.repository

import com.sprintstart.sprintstartbackend.starterwork.model.entity.TaskOrientationPacket
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TaskOrientationPacketRepository : JpaRepository<TaskOrientationPacket, UUID> {
    fun findByTaskProposalIdAndProjectId(taskProposalId: UUID, projectId: UUID): TaskOrientationPacket?

    fun deleteByTaskProposalIdAndProjectId(taskProposalId: UUID, projectId: UUID)
}
