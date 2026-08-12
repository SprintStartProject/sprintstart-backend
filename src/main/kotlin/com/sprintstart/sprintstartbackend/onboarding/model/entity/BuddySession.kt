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
 * this session plus its [BuddyMessage]s is what makes the conversation durable across visits,
 * mirroring how [SkillAssessmentSession] durably backs the stateless interviewer.
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
     * Now that the buddy is the hire's front door, conversations only get longer — re-sending the
     * whole transcript every turn is unbounded. Only the window after [summarizedCount] is sent,
     * with this summary standing in for the rest. It is a prompt-shaping device, never the
     * record: the full transcript stays in `buddy_messages`.
     */
    @Column(nullable = true, columnDefinition = "TEXT")
    var summary: String? = null,
    /**
     * How many of the oldest persisted messages [summary] covers.
     *
     * ⚠️ **This is the prompt's cursor and nothing else.** It used to answer three questions at
     * once — what the AI is sent, what the hire's transcript shows, and whether this visit already
     * has a greeting — and the last two are now [BuddyMessage.opening]'s. They came apart the
     * moment folding moved off the turn: a cursor advanced by a background pass cannot also mean
     * "the visit started here".
     */
    @Column(name = "summarized_count", nullable = false)
    var summarizedCount: Int = 0,
    /**
     * Guards the compaction swap.
     *
     * `BuddyCompactionService` reads the session, calls the model outside any transaction, then
     * writes the folded note back — and in between, a turn may have moved the cursor. It re-reads
     * and compares, but ⚠️ **a re-check is not a lock**: `backend#170` is this repo's cautionary
     * tale, where read-then-insert with no unique index started two assessment sessions at once and
     * narrowing the window did not close it. The version does close it.
     */
    @Version
    @Column(nullable = false)
    var version: Long = 0,
)
