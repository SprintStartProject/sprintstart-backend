package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "onboarding_feedback")
class OnboardingFeedback(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val userId: UUID,
    @ManyToOne
    @JoinColumn(name = "step_id", nullable = true)
    var step: OnboardingStep? = null,
    @Column(nullable = true)
    var helpful: Boolean? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var message: String,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var read: Boolean = false,
)
