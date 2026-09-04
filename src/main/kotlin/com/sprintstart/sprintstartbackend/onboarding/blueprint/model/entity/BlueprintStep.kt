package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table

@Entity
@Table(name = "blueprint_steps")
class BlueprintStep(
    blueprintPhase: BlueprintPhase,
    title: String,
    graphX: Double? = null,
    graphY: Double? = null,
    @Column(nullable = false)
    var position: Int,
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
) : BlueprintSubGraphNode(
        blueprintPhase = blueprintPhase,
        title = title,
        graphX = graphX,
        graphY = graphY,
    )
