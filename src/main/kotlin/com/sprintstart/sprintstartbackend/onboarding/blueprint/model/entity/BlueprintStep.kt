package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

@Entity
@Table(name = "blueprint_steps")
data class BlueprintStep(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blueprint_phase_id", nullable = false)
    val blueprintPhase: BlueprintPhase,
    @Column(nullable = false)
    @Version
    var revision: Long = 0,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    var description: String,
    @Column(nullable = true)
    var type: StepType,
    @Column(name = "is_ai_assisted", nullable = false, columnDefinition = "boolean not null default true")
    var aiAssisted: Boolean = false,
    @Column(nullable = true)
    var estimatedMinutes: Int,
    @OneToMany(
        mappedBy = "blueprintStep",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position")
    val blueprintTasks: MutableList<BlueprintTask> = mutableListOf(),
    @OneToMany(
        mappedBy = "blueprintStep",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    val blueprintResources: MutableList<BlueprintResource> = mutableListOf(),
    @Column(nullable = false, columnDefinition = "TEXT")
    var expectedOutcome: String,
)
