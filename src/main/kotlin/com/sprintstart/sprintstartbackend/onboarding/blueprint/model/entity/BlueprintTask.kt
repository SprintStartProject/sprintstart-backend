package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

@Entity
@Table(name = "blueprint_tasks")
class BlueprintTask(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blueprint_step_id", nullable = false)
    val blueprintStep: BlueprintStep,
    @Column(nullable = false)
    @Version
    var revision: Long = 0,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false)
    var description: String,
)
