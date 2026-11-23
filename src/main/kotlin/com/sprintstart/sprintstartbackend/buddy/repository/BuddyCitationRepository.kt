package com.sprintstart.sprintstartbackend.buddy.repository

import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyCitation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuddyCitationRepository : JpaRepository<BuddyCitation, UUID> {
    fun deleteAllByMessageSessionId(sessionId: UUID)
}
