package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoardCardRepository : JpaRepository<BoardCard, UUID> {
    /**
     * Every card on a board, dismissed ones included.
     *
     * Deliberately unfiltered: the dismissed rows are what stop the mentor re-adding a card the
     * hire has removed, so the code that ensures cards exist has to see them.
     */
    fun findAllByBoardId(boardId: UUID): List<BoardCard>
}
