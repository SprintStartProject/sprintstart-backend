package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserGoal
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserGoalRepository : JpaRepository<UserGoal, UUID> {
    fun findByUserIdAndProjectId(userId: UUID, projectId: UUID): UserGoal?

    fun deleteByUserIdAndProjectId(userId: UUID, projectId: UUID)
}
