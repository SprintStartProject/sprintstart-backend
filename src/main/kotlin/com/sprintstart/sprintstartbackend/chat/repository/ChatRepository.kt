package com.sprintstart.sprintstartbackend.chat.repository

import com.sprintstart.sprintstartbackend.chat.models.Chat
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
internal interface ChatRepository : JpaRepository<Chat, UUID> {
    @Query(
        """
        SELECT c 
        FROM Chat c
        WHERE c.status = com.sprintstart.sprintstartbackend.chat.models.ChatStatus.ACTIVE
        AND c.userId = :userId
        """,
    )
    fun findAllActiveByUserId(userId: UUID, pageable: Pageable): Page<Chat>

    /**
     * Chats the user owns *within one project*.
     *
     * Chats without a project — created before project scoping existed — are excluded by
     * definition, so they no longer appear in any list. They stay reachable by id.
     */
    @Query(
        """
        SELECT c 
        FROM Chat c 
        WHERE c.status = com.sprintstart.sprintstartbackend.chat.models.ChatStatus.ACTIVE
        AND c.userId = :userId 
        AND c.projectId = :projectId
        """,
    )
    fun findAllActiveByUserIdAndProjectId(userId: UUID, projectId: UUID, pageable: Pageable): Page<Chat>

    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<Chat>

    @Query(
        """
        SELECT c
        FROM Chat c
        WHERE c.status = com.sprintstart.sprintstartbackend.chat.models.ChatStatus.BINNED
          AND c.binnedAt < :cutoff
        """,
    )
    fun findBinnedBefore(cutoff: Instant): List<Chat>
}
