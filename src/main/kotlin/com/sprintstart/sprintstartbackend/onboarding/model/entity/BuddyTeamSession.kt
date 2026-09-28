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
 * A project manager's buddy conversation about one project's team.
 *
 * Kept apart from the manager's own [BuddySession] for two reasons. The hire's memory note is shown
 * on their board and grounds their greeting, so team talk folded into it would describe the team as
 * if it were the reader's own onboarding. And a manager of two projects must not have one project's
 * summary folded into a turn about the other: the summary is data about that team, so it carries the
 * same scope as the tools that read it.
 *
 * Its own table rather than a project column on `buddy_sessions`: the schema is built by
 * `ddl-auto: update`, which never drops a constraint, so `uq_buddy_sessions_user` would survive and
 * reject the second row for the same user.
 */
@Entity
@Table(
    name = "buddy_team_sessions",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_buddy_team_sessions_user_project", columnNames = ["user_id", "project_id"]),
    ],
)
class BuddyTeamSession(
    @Id
    override val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = true, columnDefinition = "TEXT")
    override var summary: String? = null,
    @Column(name = "summarized_count", nullable = false)
    override var summarizedCount: Int = 0,
    /** Guards the compaction swap, for the same reason as [BuddySession.version]. */
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) : BuddyMemory
