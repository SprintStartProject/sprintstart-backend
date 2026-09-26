package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BoardCardRepository : JpaRepository<BoardCard, UUID> {
    /**
     * Every card on a board, dismissed ones included.
     *
     * Deliberately unfiltered: the dismissed rows are what stop the mentor re-adding a card the
     * hire has removed, so the code that ensures cards exist has to see them.
     */
    fun findAllByBoardId(boardId: UUID): List<BoardCard>

    /**
     * One card, locked until the surrounding transaction ends.
     *
     * For edits that read a card's content, change part of it and write it back whole. Without
     * the lock, two of those running at once each read the same payload, and the second write
     * silently drops the first one's change.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BoardCard c where c.id = :id")
    fun findLockedById(id: UUID): BoardCard?

    fun deleteAllByBoardId(boardId: UUID)
}
