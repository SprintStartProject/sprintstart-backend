package com.sprintstart.sprintstartbackend.arrival.repository

import com.sprintstart.sprintstartbackend.arrival.model.entity.ArrivalStepState
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

    fun deleteAllByUserId(userId: UUID)
}
