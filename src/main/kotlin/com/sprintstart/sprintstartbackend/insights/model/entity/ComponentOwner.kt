package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * Assigns a user as an owner of a component (an `owner/repo` identifier).
 *
 * Ownership is maintained independently of the AI-detected knowledge gaps: a component keeps its
 * owners across refreshes. When a knowledge gap is served, its owners are resolved by matching the
 * gap's component against these rows. A component may have several owners, so one row is stored per
 * (component, user) pair.
 */
@Entity
@Table(
    name = "component_owner",
    uniqueConstraints = [UniqueConstraint(columnNames = ["component", "user_id"])],
)
class ComponentOwner(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val component: String,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
)
