package com.sprintstart.sprintstartbackend.user.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * How one user has arranged their dashboard.
 *
 * One row per user, created on first write: a user who has not arranged anything gets the default
 * dashboard, and an empty row saying so would tell nobody anything.
 *
 * Keyed by user and not by project, because the dashboard is one per person — a hire on two
 * projects has two board arrangements (see `board_structures`) and one dashboard. The widgets
 * themselves decide per project what they show.
 *
 * The arrangement is JSON in [payload], read and written whole, the same bargain the board's
 * arrangement makes. [version] is the client's layout version it was written under; see
 * `DashboardLayoutService` for what happens when it no longer matches.
 */
@Entity
@Table(
    name = "dashboard_layouts",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_dashboard_layouts_user", columnNames = ["user_id"]),
    ],
)
class DashboardLayout(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(nullable = false)
    var version: Int,
    @Column(columnDefinition = "TEXT", nullable = false)
    var payload: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
