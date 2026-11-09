package com.sprintstart.sprintstartbackend.knowledge.repository

import com.sprintstart.sprintstartbackend.knowledge.model.entity.LoggedQuestion
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface LoggedQuestionRepository : JpaRepository<LoggedQuestion, UUID> {
    fun findAllByProjectId(projectId: UUID): List<LoggedQuestion>

    fun countByProjectId(projectId: UUID): Long

    fun countByProjectIdAndAskedAtGreaterThanEqual(projectId: UUID, since: Instant): Long

    fun deleteAllBySessionId(sessionId: UUID)

    fun deleteAllByUserId(userId: UUID)
}
