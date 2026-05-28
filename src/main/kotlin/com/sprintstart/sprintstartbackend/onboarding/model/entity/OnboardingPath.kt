package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "onboarding_paths")
class OnboardingPath(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val userId: UUID,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @OneToMany(
        mappedBy = "path",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val phases: MutableList<OnboardingPhase> = mutableListOf(),
)

