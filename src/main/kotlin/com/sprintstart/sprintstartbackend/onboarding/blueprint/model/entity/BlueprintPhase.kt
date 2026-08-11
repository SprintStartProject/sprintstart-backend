package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "blueprint_phases")
class BlueprintPhase(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "blueprint_path_id")
    val blueprintPath: BlueprintPath,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false)
    var description: String,
    @OneToMany(
        mappedBy = "blueprintPhase",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val blueprintSteps: MutableList<BlueprintStep> = mutableListOf(),
    @OneToMany(
        mappedBy = "blueprintPhase",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val blueprintCheckQuestions: MutableList<BlueprintCheckQuestion> = mutableListOf(),
)
