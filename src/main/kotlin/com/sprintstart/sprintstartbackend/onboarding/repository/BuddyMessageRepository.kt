package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuddyMessageRepository : JpaRepository<BuddyMessage, UUID> {
    fun findAllBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<BuddyMessage>
}
