package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "onboarding_resources")
class OnboardingResource (
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "step_id", nullable = false)
    val step: OnboardingStep,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false)
    var description: String,
    @Column(nullable = false)
    var url: String,
)
