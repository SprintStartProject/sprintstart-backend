package com.sprintstart.sprintstartbackend.board.repository

import com.sprintstart.sprintstartbackend.board.model.entity.Board
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoardRepository : JpaRepository<Board, UUID> {
    fun findByUserIdAndProjectId(userId: UUID, projectId: UUID): Board?

    /** Whether the board exists, for callers that must not create one by asking. */
    fun existsByUserIdAndProjectId(userId: UUID, projectId: UUID): Boolean

    fun findAllByUserId(userId: UUID): List<Board>

    fun deleteAllByUserId(userId: UUID)
}
