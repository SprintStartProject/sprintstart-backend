package com.sprintstart.sprintstartbackend.starterwork.repository

import com.sprintstart.sprintstartbackend.starterwork.model.entity.UserCompetencyState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserCompetencyStateRepository : JpaRepository<UserCompetencyState, UUID> {
    fun findAllByUserId(userId: UUID): List<UserCompetencyState>

    fun findAllByUserIdIn(userIds: Collection<UUID>): List<UserCompetencyState>

    fun findByUserIdAndCompetencyKey(userId: UUID, competencyKey: String): UserCompetencyState?

    fun deleteAllByUserId(userId: UUID)
}
