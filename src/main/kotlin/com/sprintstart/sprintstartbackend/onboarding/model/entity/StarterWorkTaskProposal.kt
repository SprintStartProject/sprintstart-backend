package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * An AI-mined starter-work task (a GitHub issue).
 *
 * A hire's goal points at this row directly, so reviewing one creates nothing -- it only lifts the
 * demotion [com.sprintstart.sprintstartbackend.onboarding.service.StarterWorkMatcher] applies while
 * nobody has looked.
 */
@Entity
@Table(name = "starter_work_task_proposals")
class StarterWorkTaskProposal(
    @Id
    val id: UUID = UUID.randomUUID(),
    // The backend's stable GitHub issue identifier (e.g. "github:org/repo:ISSUE:123"), unique
    // per proposal so the same issue is never mined into two rows.
    @Column(name = "source_id", nullable = false, unique = true)
    val sourceId: String,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    val summary: String? = null,
    @Column(nullable = true, columnDefinition = "TEXT")
    val rationale: String? = null,
    @Column(name = "source_url", nullable = true)
    val sourceUrl: String? = null,
    // The competency keys the AI judged this task exercises. One of the four signals the matcher
    // ranks a task by for a hire; it says what the work touches, never who may claim it.
    @ElementCollection
    @CollectionTable(
        name = "starter_work_task_proposal_competency_keys",
        joinColumns = [JoinColumn(name = "starter_work_task_proposal_id")],
    )
    @Column(name = "competency_key", nullable = false)
    val competencyKeys: MutableList<String> = mutableListOf(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProposalStatus = ProposalStatus.LIVE,
    /**
     * Whether a person has actually looked at this task.
     *
     * A mined task is claimable the moment it is mined — nobody has to work through a queue before
     * a hire can be pointed at anything. What review buys is *confidence*, so it is expressed as
     * rank rather than as a gate: `StarterWorkMatcher` demotes an unreviewed task, capped below any
     * single positive signal, so one that fits a hire perfectly still beats a reviewed one that does
     * not. Human attention improves the system instead of blocking it.
     *
     * Hand-authored tasks are reviewed by construction: somebody wrote them.
     */
    @Column(nullable = false)
    var reviewed: Boolean = false,
    /**
     * Whether a PM has flagged this task as suitable for **Task 0** — the trivial first
     * task a new hire is auto-assigned once their environment is ready, to walk the
     * branch → PR → review → merge loop once while the stakes are nil. A deliberate PM
     * choice, not a default.
     */
    @Column(name = "task_zero_eligible", nullable = false)
    var taskZeroEligible: Boolean = false,
    /**
     * Which onboarding track this task is work *for*, or null when it suits anybody.
     *
     * Without it a Scrum Master's suggested first tasks were mined GitHub issues -- correct work,
     * for somebody else's job. Null is the deliberate default and means "any track": a task nobody
     * has scoped is offered to everybody, which is exactly how every task behaved before tracks
     * existed, and mining cannot know which role an issue suits.
     */
    @Column(name = "onboarding_track_key", nullable = true)
    var onboardingTrackKey: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "decided_at", nullable = true)
    var decidedAt: Instant? = null,
    @Column(name = "rejection_reason", nullable = true, columnDefinition = "TEXT")
    var rejectionReason: String? = null,
)
