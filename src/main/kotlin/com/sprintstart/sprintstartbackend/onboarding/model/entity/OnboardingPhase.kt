package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "onboarding_phases")
class OnboardingPhase(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "path_id", nullable = false)
    val path: OnboardingPath,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false, columnDefinition = "TEXT")
    var title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String,
    @OneToMany(
        mappedBy = "phase",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val steps: MutableList<OnboardingStep> = mutableListOf(),
    @OneToMany(
        mappedBy = "phase",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val checkQuestions: MutableList<PhaseCheckQuestion> = mutableListOf(),
    @OneToMany(
        mappedBy = "phase",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("createdAt ASC")
    val checkAttempts: MutableList<PhaseCheckAttempt> = mutableListOf(),
    @Column(nullable = true)
    var graphX: Double? = null,
    @Column(nullable = true)
    var graphY: Double? = null,
    @ManyToMany
    @JoinTable(
        name = "onboarding_phase_blockers",
        joinColumns = [JoinColumn(name = "blocked_phase_id")],
        inverseJoinColumns = [JoinColumn(name = "blocker_phase_id")],
    )
    val blockedBy: MutableSet<OnboardingPhase> = mutableSetOf(),
)
