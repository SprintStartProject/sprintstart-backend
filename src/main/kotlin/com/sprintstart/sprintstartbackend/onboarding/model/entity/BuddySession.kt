package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * A hire's ongoing onboarding buddy companion -- one continuous conversation per user, unlike the
 * general-purpose `chat` module's multiple user-created chats. The AI buddy endpoint is stateless;
 * this session plus its [BuddyMessage]s is what makes the conversation durable across visits.
 */
@Entity
@Table(
    name = "buddy_sessions",
    uniqueConstraints = [UniqueConstraint(name = "uq_buddy_sessions_user", columnNames = ["user_id"])],
)
class BuddySession(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    /**
     * The AI-written running summary of this conversation's oldest [summarizedCount] messages.
     *
     * The buddy is the hire's front door, so conversations only get longer — re-sending the
     * whole transcript every turn is unbounded. Only the window after [summarizedCount] is sent,
     * with this summary standing in for the rest. It is a prompt-shaping device, never the
     * record: the full transcript stays in `buddy_messages`.
     */
    @Column(nullable = true, columnDefinition = "TEXT")
    var summary: String? = null,
    /**
     * How many of the oldest persisted messages [summary] covers.
     *
     * This is the prompt's cursor and nothing else: it says what the AI is sent, never what the
     * hire's transcript shows or whether this visit already has a greeting. Both of those are
     * [BuddyMessage.opening]'s to answer, because a cursor advanced by a background folding pass
     * cannot also mean "the visit started here".
     */
    @Column(name = "summarized_count", nullable = false)
    var summarizedCount: Int = 0,
    /**
     * Guards the compaction swap.
     *
     * `BuddyCompactionService` reads the session, calls the model outside any transaction, then
     * writes the folded note back — and in between, a turn may have moved the cursor. It re-reads
     * and compares, but a re-check is not a lock: it narrows the window without closing it, the
     * way read-then-insert with no unique index does. The version closes it.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0,
)
