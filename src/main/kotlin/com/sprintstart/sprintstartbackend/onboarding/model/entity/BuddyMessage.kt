package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One turn in a [BuddySession]'s conversation. Referenced by plain FK rather than a cascaded
 * collection on [BuddySession] -- mirrors the `chat` module's `Chat`/`ChatMessage` split, since a
 * long-running buddy conversation shouldn't be eagerly loaded through its parent.
 *
 * Deliberately carries no citations -- resolving/persisting them would need the `chat` module's
 * internal `ArtifactLookupService`, which isn't exposed cross-module; live `citation` SSE events
 * still reach the client during the stream, just aren't persisted for replay.
 */
@Entity
@Table(name = "buddy_messages")
class BuddyMessage(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    val session: BuddySession,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: BuddyMessageRole,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    /**
     * True for the greeting that opens a visit, false for every reply inside one.
     *
     * ⚠️ **This is what makes "a visit" a fact about the transcript rather than a side effect of
     * compaction.** Both "does this visit already have a greeting?" and "what does the hire see?"
     * used to be answered from [BuddySession.summarizedCount], which worked only because opening a
     * visit advanced that cursor to the end of the transcript. Once folding moved to a background
     * pass that cursor stopped marking anything a person would recognise, and borrowing it would
     * have regenerated a greeting on every refresh — the bug `#176` fixed.
     *
     * A visit ends when the hire speaks, so the marker is read as *the last message is an opening*,
     * never as *an opening exists*.
     */
    @Column(nullable = false)
    val opening: Boolean = false,
)
