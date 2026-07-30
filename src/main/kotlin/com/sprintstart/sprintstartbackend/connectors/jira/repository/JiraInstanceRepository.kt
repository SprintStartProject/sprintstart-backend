package com.sprintstart.sprintstartbackend.connectors.jira.repository

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface JiraInstanceRepository : JpaRepository<JiraInstance, String> {
    @Query(
        """
        SELECT i FROM JiraInstance i
        JOIN i.projectIds p
        WHERE p = :projectId
        """,
    )
    fun findByProjectId(projectId: UUID): List<JiraInstance>
}
