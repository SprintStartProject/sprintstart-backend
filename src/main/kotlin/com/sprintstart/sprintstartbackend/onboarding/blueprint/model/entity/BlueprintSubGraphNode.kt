package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

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
import jakarta.persistence.Version
import java.util.UUID

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
abstract class BlueprintSubGraphNode(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    @Version
    var revision: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blueprint_phase_id", nullable = false)
    val blueprintPhase: BlueprintPhase,
    @Column(nullable = false)
    var title: String,
    var graphX: Double? = null,
    var graphY: Double? = null,
    @ManyToMany
    @JoinTable(
        name = "blueprint_graph_node_blockers",
        joinColumns = [JoinColumn(name = "blocked_node_id")],
        inverseJoinColumns = [JoinColumn(name = "blocker_node_id")],
    )
    val blockedBy: MutableSet<BlueprintSubGraphNode> = mutableSetOf(),
)
