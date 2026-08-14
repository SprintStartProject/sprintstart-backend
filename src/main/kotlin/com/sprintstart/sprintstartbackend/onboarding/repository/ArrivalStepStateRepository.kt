package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStepState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ArrivalStepStateRepository : JpaRepository<ArrivalStepState, UUID> {
    fun findAllByUserId(userId: UUID): List<ArrivalStepState>

    fun findByUserIdAndStepKeyAndProjectIdIsNull(userId: UUID, stepKey: String): ArrivalStepState?

    fun findByUserIdAndStepKeyAndProjectId(
        userId: UUID,
        stepKey: String,
        projectId: UUID,
    ): ArrivalStepState?
}
