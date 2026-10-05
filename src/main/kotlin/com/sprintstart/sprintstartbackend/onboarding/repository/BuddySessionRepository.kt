package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface BuddySessionRepository : JpaRepository<BuddySession, UUID> {
    fun findByUserId(userId: UUID): List<BuddySession>

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<BuddySession>

    fun findByUserIdAndStatusOrderByCreatedAtDesc(userId: UUID, status: BuddySessionStatus): List<BuddySession>

    fun findByIdAndUserId(sessionId: UUID, userId: UUID): BuddySession?

    fun findBinnedBefore(cutoff: Instant): List<BuddySession>

    fun deleteAllByUserId(userId: UUID)
}
