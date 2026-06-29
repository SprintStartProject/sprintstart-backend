package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "blueprint_steps")
class BlueprintStep(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "blueprint_id", nullable = false)
    val blueprint: Blueprint,
    @Column(nullable = false)
    val stepId: String,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    val description: String? = null,
    @Column(nullable = true)
    val minExperience: String? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    val audience: String = "",
    @Column(nullable = false)
    val position: Int,
    @Column(nullable = false)
    val requirement: String = "recommended",
    @Column(nullable = false)
    val invariant: Boolean = false,
)
