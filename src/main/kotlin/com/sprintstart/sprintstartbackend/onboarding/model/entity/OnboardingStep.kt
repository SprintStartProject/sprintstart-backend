package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "onboarding_steps")
class OnboardingStep(
    id: UUID = UUID.randomUUID(),
    phase: OnboardingPhase,
    @Column(nullable = false)
    var position: Int,
    title: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    var description: String,
    @Column(nullable = true)
    var type: StepType,
    @Column(name = "is_ai_assisted", nullable = false, columnDefinition = "boolean not null default true")
    var aiAssisted: Boolean = true,
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
    @Column(nullable = false, columnDefinition = "TEXT")
    var expectedOutcome: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: StepStatus,
    @Column(nullable = true)
    var startedAt: Instant? = null,
    @Column(nullable = true)
    var completedAt: Instant? = null,
    @OneToMany(
        mappedBy = "step",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("createdAt ASC")
    val skips: MutableList<OnboardingSkip> = mutableListOf(),
    @OneToMany(
        mappedBy = "step",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("createdAt ASC")
    val feedback: MutableList<OnboardingFeedback> = mutableListOf(),
    graphX: Double? = null,
    graphY: Double? = null,
) : OnboardingSubGraphNode(
        id = id,
        phase = phase,
        title = title,
        graphX = graphX,
        graphY = graphY,
    )
