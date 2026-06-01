package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "onboarding_steps")
class OnboardingStep(
    @Id
    val id: UUID = UUID.randomUUID(),
    // This is a foreign key into onboarding_paths
    @ManyToOne
    @JoinColumn(name = "phase_id", nullable = false)
    val phase: OnboardingPhase,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = true)
    var description: String,
    @Column(nullable = true)
    var type: StepType,
    @Column(nullable = true)
    var estimatedMinutes: Int,
    @OneToMany(
        mappedBy = "step",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position")
    val tasks: MutableList<OnboardingTask> = mutableListOf(),
    @OneToMany(
        mappedBy = "step",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    val resources: MutableList<OnboardingResource> = mutableListOf(),
    @Column(nullable = false)
    var expectedOutcome: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: StepStatus,
    @Column(nullable = true)
    var completedAt: Instant? = null,
    @Column(nullable = true)
    var skipReason: String? = null,
)
