package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "phase_check_attempts")
class PhaseCheckAttempt(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "phase_id", nullable = false)
    val phase: OnboardingPhase,
    @Column(nullable = false)
    val userId: UUID,
    @Column(nullable = false)
    val passed: Boolean,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @OneToMany(
        mappedBy = "attempt",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    val answers: MutableList<PhaseCheckAnswer> = mutableListOf(),
)
