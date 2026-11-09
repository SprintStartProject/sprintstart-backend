package com.sprintstart.sprintstartbackend.buddy.repository

import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuddyMessageRepository : JpaRepository<BuddyMessage, UUID> {
    fun findAllBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<BuddyMessage>

    fun deleteAllBySessionId(sessionId: UUID)
}
