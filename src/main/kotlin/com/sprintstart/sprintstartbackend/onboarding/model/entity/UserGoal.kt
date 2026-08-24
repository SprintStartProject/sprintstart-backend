package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * The starter-work task a hire has claimed as their goal, per project.
 *
 * The north star is time-to-first-contribution, so a hire aims at a piece of real work rather than
 * at a position in a curriculum. This row is what makes that concrete.
 *
 * Stored rather than derived. Hire→task matching is a ranking, so deriving it per read
 * would let a hire's destination change under them between two page loads. The hire claims one from
 * their ranked matches and it stays claimed until they change it.
 */
@Entity
@Table(
    name = "user_goals",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_user_goals_user_project", columnNames = ["user_id", "project_id"]),
    ],
)
class UserGoal(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    /** The starter-work task being worked toward. */
    @Column(name = "source_proposal_id", nullable = false)
    var sourceProposalId: UUID,
    @Column(name = "claimed_at", nullable = false)
    var claimedAt: Instant = Instant.now(),
)
