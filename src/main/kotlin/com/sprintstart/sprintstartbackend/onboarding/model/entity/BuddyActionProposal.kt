package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * A change the buddy offered a project's manager in team mode, stored until they decide.
 *
 * Stored rather than echoed through the client, which is what the hire's actions do: there, every
 * action adds its own nullable fields to the stream event and the confirm request, and a field
 * dropped anywhere on the way surfaces as a polite refusal instead of an error. Here the client
 * sends back only [id]; what runs is exactly what was previewed, because it is read from this row.
 *
 * [params] is the action's own JSON, opaque to everything but the action that wrote it. [preview]
 * is the text the manager confirmed — kept, so what they agreed to can be shown later.
 */
@Entity
@Table(name = "buddy_action_proposals")
class BuddyActionProposal(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(nullable = false)
    val action: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val params: String,
    @Column(nullable = false)
    val label: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val preview: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val risk: BuddyProposalRisk,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BuddyProposalStatus = BuddyProposalStatus.PROPOSED,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "decided_at", nullable = true)
    var decidedAt: Instant? = null,
    @Column(name = "result_message", nullable = true, columnDefinition = "TEXT")
    var resultMessage: String? = null,
    @Version
    @Column(nullable = false)
    var version: Long = 0,
)
