package com.sprintstart.sprintstartbackend.starterwork.repository

import com.sprintstart.sprintstartbackend.starterwork.model.entity.UserGoal
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserGoalRepository : JpaRepository<UserGoal, UUID> {
    fun findByUserIdAndProjectId(userId: UUID, projectId: UUID): UserGoal?

    fun deleteByUserIdAndProjectId(userId: UUID, projectId: UUID)

    fun deleteAllByUserId(userId: UUID)
}
