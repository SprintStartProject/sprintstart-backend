package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipRequestStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "onboarding_skip_requests")
class SkipRequest(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val stepId: UUID,
    @Column(nullable = false)
    val reason: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SkipRequestStatus = SkipRequestStatus.PENDING,
    @Column(nullable = true)
    var reviewComment: String? = null,
    @Column(nullable = true)
    var reviewedAt: Instant? = null,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
