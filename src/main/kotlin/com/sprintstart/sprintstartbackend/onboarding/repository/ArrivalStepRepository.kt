package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ArrivalStepRepository : JpaRepository<ArrivalStep, UUID> {
    /** Company-wide steps — the ones every hire gets, regardless of project. */
    fun findAllByProjectIdIsNullOrderByPositionAsc(): List<ArrivalStep>

    fun findAllByProjectIdInOrderByPositionAsc(projectIds: Collection<UUID>): List<ArrivalStep>

    fun findByKeyAndProjectIdIsNull(key: String): ArrivalStep?

    fun findByKeyAndProjectId(key: String, projectId: UUID): ArrivalStep?

    fun existsByKeyAndProjectIdIsNull(key: String): Boolean

    fun existsByKeyAndProjectId(key: String, projectId: UUID): Boolean
}
