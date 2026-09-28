package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagram
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoardDiagramRepository : JpaRepository<BoardDiagram, UUID> {
    /** Every cached picture for these cards, so a board read fetches them in one query, not N. */
    fun findAllByCardIdIn(cardIds: Collection<UUID>): List<BoardDiagram>
}
