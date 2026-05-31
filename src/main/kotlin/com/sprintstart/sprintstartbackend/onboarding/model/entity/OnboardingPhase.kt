package com.sprintstart.sprintstartbackend.onboarding.model.entity

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
@Table(name = "onboarding_phases")
class OnboardingPhase (
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "path_id", nullable = false)
    val path: OnboardingPath,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false)
    var description: String,
    @OneToMany(
        mappedBy = "phase",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val steps: MutableList<OnboardingStep> = mutableListOf(),
)
