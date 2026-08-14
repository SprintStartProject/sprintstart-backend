package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuddySessionRepository : JpaRepository<BuddySession, UUID> {
    fun findByUserId(userId: UUID): BuddySession?
}
