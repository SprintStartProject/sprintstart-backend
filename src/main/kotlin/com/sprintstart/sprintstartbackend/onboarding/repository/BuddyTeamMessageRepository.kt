package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuddyTeamMessageRepository : JpaRepository<BuddyTeamMessage, UUID> {
    fun findAllBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<BuddyTeamMessage>

    fun deleteAllBySessionId(sessionId: UUID)
}
