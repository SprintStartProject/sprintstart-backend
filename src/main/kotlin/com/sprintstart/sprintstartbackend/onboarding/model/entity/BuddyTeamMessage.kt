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
 * One turn in a [BuddyTeamSession]'s conversation.
 *
 * Mirrors [BuddyMessage] field for field, including [opening], which marks the greeting that opens a
 * visit and is read the same way: *the last message is an opening*, never *an opening exists*.
 */
@Entity
@Table(name = "buddy_team_messages")
class BuddyTeamMessage(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    val session: BuddyTeamSession,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: BuddyMessageRole,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    val opening: Boolean = false,
    /**
     * The team areas this reply opened, as a comma-separated list of area names; null when it opened
     * none, and for every message written before the column existed.
     *
     * Read back on the manager's *next* message so that an area opened to draft something is still
     * open when they say "yes, send it". The transcript holds text only, so without this the tools that
     * make the change are gone by the time the manager approves, and the model has nothing to call.
     */
    @Column(name = "opened_areas", nullable = true)
    val openedAreas: String? = null,
)
