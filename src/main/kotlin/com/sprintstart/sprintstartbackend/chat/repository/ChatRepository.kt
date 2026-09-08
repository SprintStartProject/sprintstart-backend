package com.sprintstart.sprintstartbackend.chat.repository

import com.sprintstart.sprintstartbackend.chat.models.Chat
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
internal interface ChatRepository : JpaRepository<Chat, UUID> {
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Chat>

    /**
     * Chats the user owns *within one project*.
     *
     * Chats without a project — created before project scoping existed — are excluded by
     * definition, so they no longer appear in any list. They stay reachable by id.
     */
    fun findAllByUserIdAndProjectId(userId: UUID, projectId: UUID, pageable: Pageable): Page<Chat>

    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<Chat>
}
