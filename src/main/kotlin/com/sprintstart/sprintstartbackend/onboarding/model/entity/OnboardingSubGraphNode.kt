package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import java.util.UUID

/**
 * Represents a node inside an onboarding phase graph.
 *
 * Steps and knowledge-check questions share coordinates and dependency edges so a
 * personalized onboarding path retains the subgraph authored in its source blueprint.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
abstract class OnboardingSubGraphNode(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    val phase: OnboardingPhase,
    @Column(nullable = false, columnDefinition = "TEXT")
    var title: String,
    @Column(nullable = true)
    var graphX: Double? = null,
    @Column(nullable = true)
    var graphY: Double? = null,
    @ManyToMany
    @JoinTable(
        name = "onboarding_sub_graph_node_blockers",
        joinColumns = [JoinColumn(name = "blocked_node_id")],
        inverseJoinColumns = [JoinColumn(name = "blocker_node_id")],
    )
    val blockedBy: MutableSet<OnboardingSubGraphNode> = mutableSetOf(),
)
