package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "onboarding_tasks")
class OnboardingTask (
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "step_id", nullable = false)
    val step: OnboardingStep,
    @Column(nullable = false)
    val position: Int,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false)
    val description: String,
    @Column(nullable = false)
    val finished: Boolean = false,
)
