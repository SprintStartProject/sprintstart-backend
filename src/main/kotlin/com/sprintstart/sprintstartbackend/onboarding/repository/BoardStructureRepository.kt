package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructure
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface BoardStructureRepository : JpaRepository<BoardStructure, UUID> {
    fun findByBoardId(boardId: UUID): BoardStructure?

    fun deleteAllByBoardIdIn(boardIds: Collection<UUID>)

    /**
     * Writes a board's arrangement, whether or not it already has one.
     *
     * One statement rather than "look, then insert or update". Two tabs saving a board's first
     * arrangement both saw no row and both inserted: one of them got a constraint violation and a
     * 500 for what is meant to be last-write-wins, and against a database missing
     * `uq_board_structures_board` they both succeeded and left two rows, after which
     * [findByBoardId] is a coin toss for that board forever.
     *
     * `ON CONFLICT (board_id)` makes the two cases one, and makes the conflict resolve the way the
     * endpoint says it does: the later write replaces the earlier one. [id] is used only when this
     * inserts; on the update path the row keeps the identity and `created_at` it already had.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO board_structures (id, board_id, payload, created_at, updated_at)
            VALUES (:id, :boardId, :payload, :now, :now)
            ON CONFLICT (board_id) DO UPDATE
            SET payload = EXCLUDED.payload, updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("id") id: UUID,
        @Param("boardId") boardId: UUID,
        @Param("payload") payload: String,
        @Param("now") now: Instant,
    )
}
