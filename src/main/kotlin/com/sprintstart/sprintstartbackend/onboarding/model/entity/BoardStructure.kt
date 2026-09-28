package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * How one hire has arranged one board.
 *
 * One row per board, created on first write rather than alongside the board: a hire who has not
 * arranged anything has nothing to say, and an empty row saying so is a row that has to be kept in
 * step with a board it tells you nothing about.
 *
 * The arrangement itself is JSON in [payload] — see [BoardStructurePayload] for what is in it and
 * why it is one column rather than six tables.
 */
@Entity
@Table(
    name = "board_structures",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_board_structures_board", columnNames = ["board_id"]),
    ],
)
class BoardStructure(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "board_id", nullable = false)
    val boardId: UUID,
    @Column(columnDefinition = "TEXT", nullable = false)
    var payload: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    /**
     * When this was last written.
     *
     * Returned with the arrangement, because the client writes the whole thing on every change and
     * two tabs will eventually disagree: last write wins, and this is what lets a client notice
     * that it lost.
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
