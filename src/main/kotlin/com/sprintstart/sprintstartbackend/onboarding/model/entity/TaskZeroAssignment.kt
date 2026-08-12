package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * The trivial first task a new hire was auto-assigned once their environment came up.
 *
 * Task 0 exists to prove the branch → PR → review → merge loop works end to end while the stakes
 * are nil, put the hire's name in the history, and create the first real interaction with their
 * reviewer — not to teach anything. Completing it credits **nothing** in the competency ledger;
 * there is no [com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState]
 * write anywhere in this flow. It proves the loop, not a competency.
 *
 * One per hire per project. Assignment is automatic on environment readiness and undoable — removing
 * the row frees the task for someone else, because a Task 0 (a real typo or doc fix) is a single
 * piece of wanted work, not a fabricated exercise that can be handed to two people at once.
 */
@Entity
@Table(name = "task_zero_assignments")
class TaskZeroAssignment(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "hire_id", nullable = false)
    val hireId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    /** The approved, Task-0-eligible starter-work proposal that was assigned. */
    @Column(name = "proposal_id", nullable = false)
    val proposalId: UUID,
    @Column(name = "assigned_at", nullable = false)
    val assignedAt: Instant = Instant.now(),
)
