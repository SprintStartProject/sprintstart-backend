package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime
import java.util.UUID

interface BuddyMessageRepository : JpaRepository<BuddyMessage, UUID> {
    fun findAllBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<BuddyMessage>

    fun findAllByRoleAndSessionProjectId(role: BuddyMessageRole, projectId: UUID): List<BuddyMessage>

    fun countByRoleAndSessionProjectId(role: BuddyMessageRole, projectId: UUID): Long

    fun countByRoleAndSessionProjectIdAndCreatedAtGreaterThanEqual(
        role: BuddyMessageRole,
        projectId: UUID,
        since: OffsetDateTime,
    ): Long

    fun deleteAllBySessionId(sessionId: UUID)
}
