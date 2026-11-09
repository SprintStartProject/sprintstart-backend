package com.sprintstart.sprintstartbackend.teamspace.repository

import com.sprintstart.sprintstartbackend.teamspace.model.entity.BuddyTeamMessage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuddyTeamMessageRepository : JpaRepository<BuddyTeamMessage, UUID> {
    fun findAllBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<BuddyTeamMessage>

    fun deleteAllBySessionId(sessionId: UUID)
}
