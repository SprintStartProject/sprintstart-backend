package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface BuddySessionRepository : JpaRepository<BuddySession, UUID> {
    fun findByUserId(userId: UUID): List<BuddySession>

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<BuddySession>

    fun findByUserIdAndStatusOrderByCreatedAtDesc(userId: UUID, status: BuddySessionStatus): List<BuddySession>

    fun findByIdAndUserId(sessionId: UUID, userId: UUID): BuddySession?

    /**
     * Names a session that has no title yet, and leaves a named one alone.
     *
     * One statement rather than load-and-save: two first messages sent to the same session at once
     * both find it untitled, and the slower save would fail on the version the faster one bumped,
     * costing that message its reply. The version is still bumped, so a compaction holding a copy
     * from before cannot write the empty title back.
     *
     * @return 1 when this call named the session, 0 when it already had a title.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        """UPDATE BuddySession s
           SET s.title = :title, s.version = s.version + 1
           WHERE s.id = :id AND TRIM(s.title) = ''""",
    )
    fun nameIfUntitled(id: UUID, title: String): Int

    fun findByStatusAndBinnedAtBefore(status: BuddySessionStatus, cutoff: Instant): List<BuddySession>

    fun countByUserId(userId: UUID): Long

    fun deleteAllByUserId(userId: UUID)
}
