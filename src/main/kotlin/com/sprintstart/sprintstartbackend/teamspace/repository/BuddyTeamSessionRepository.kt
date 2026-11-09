package com.sprintstart.sprintstartbackend.teamspace.repository

import com.sprintstart.sprintstartbackend.teamspace.model.entity.BuddyTeamSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuddyTeamSessionRepository : JpaRepository<BuddyTeamSession, UUID> {
    fun findByUserIdAndProjectId(userId: UUID, projectId: UUID): BuddyTeamSession?

    fun findAllByUserId(userId: UUID): List<BuddyTeamSession>

    fun deleteAllByUserId(userId: UUID)
}
