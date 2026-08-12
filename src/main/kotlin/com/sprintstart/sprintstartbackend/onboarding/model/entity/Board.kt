package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * A hire's persistent working surface on one project.
 *
 * Keyed by `(userId, projectId)`, and **created on first read** rather than when somebody joins,
 * so nobody accumulates empty boards for projects they never onboard on.
 */
@Entity
@Table(
    name = "boards",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_boards_user_project", columnNames = ["user_id", "project_id"]),
    ],
)
class Board(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
