package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface BoardCardRepository : JpaRepository<BoardCard, UUID> {
    /**
     * Every card on a board, dismissed ones included.
     *
     * Deliberately unfiltered: the dismissed rows are what stop the mentor re-adding a card the
     * hire has removed, so the code that ensures cards exist has to see them.
     */
    fun findAllByBoardId(boardId: UUID): List<BoardCard>

    fun deleteAllByBoardId(boardId: UUID)

    /**
     * Removes the cards of kinds the catalog no longer has.
     *
     * Native, because these rows cannot be loaded at all: their `kind` is not a [BoardCardKind] any
     * more, so any read through the entity would fail on them. `PATH_TO_FIRST_CONTRIBUTION` was the
     * joined -> first-accepted-work card, retired when onboarding became the blueprint path (#311).
     *
     * Compared as text: where the column is a database enum (H2) the old value is no longer one of
     * its members, and comparing the enum with it directly is an error rather than no match.
     */
    @Modifying
    @Transactional
    @Query(
        "DELETE FROM board_cards WHERE CAST(kind AS VARCHAR(64)) IN ('PATH_TO_FIRST_CONTRIBUTION')",
        nativeQuery = true,
    )
    fun deleteRetiredKinds(): Int
}
