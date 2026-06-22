package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "onboarding_skips")
class OnboardingSkip(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "step_id", nullable = false)
    var step: OnboardingStep,
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: SkipStatus = SkipStatus.WAITING,
    @Column(nullable = false)
    var reason: String,
    @Column(nullable = true)
    var reviewComment: String? = null,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = true)
    var resolvedAt: Instant,
)
