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
    // Mutable, unlike the fields around it: title, sourceId and sourceUrl are the tracker's facts
    // and must never drift from it, while this is our own note about the issue. Promotion rewrites
    // it when reviving a stale row, because the person vouching for it then is the one whose note
    // it now is.
    @Column(nullable = true, columnDefinition = "TEXT")
    var summary: String? = null,
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
     * Whether a PM has flagged this task as suitable for Task 0 — the trivial first
     * task a new hire is auto-assigned once their environment is ready, to walk the
     * branch → PR → review → merge loop once while the stakes are nil. A deliberate PM
     * choice, not a default.
     */
    @Column(name = "task_zero_eligible", nullable = false)
    var taskZeroEligible: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "decided_at", nullable = true)
    var decidedAt: Instant? = null,
    @Column(name = "rejection_reason", nullable = true, columnDefinition = "TEXT")
    var rejectionReason: String? = null,
    /**
     * Whether the issue had somebody on it when reconciliation last looked.
     *
     * Three-valued, mirroring `IngestedIssue.hasAssignee`: null means *nobody has checked, or the
     * tracker never said*, and only a definite `true` means somebody has this. It is written by
     * reconciliation rather than read live because
     * [com.sprintstart.sprintstartbackend.onboarding.service.StarterWorkMatcher] ranks the whole
     * pool on every board load, and a per-task lookup there would put one read per pool row in
     * front of a page.
     *
     * Note what it does *not* say: the corpus records that an issue is assigned, never to whom. So
     * this reads as "somebody has this", and a hire who was assigned the issue at its source
     * demotes their own task. That is the honest reading of the data available, and it demotes
     * rather than hides, so the cost of being wrong is a lower rank and not a missing task.
     */
    @Column(name = "source_has_assignee", nullable = true)
    var sourceHasAssignee: Boolean? = null,
    /**
     * When reconciliation last compared this row against its source. Null until it first has.
     *
     * Kept so that "nobody has checked" and "checked, and the tracker said nothing" stay
     * distinguishable — the same distinction [sourceHasAssignee] draws, one level up.
     */
    @Column(name = "source_checked_at", nullable = true)
    var sourceCheckedAt: Instant? = null,
)
