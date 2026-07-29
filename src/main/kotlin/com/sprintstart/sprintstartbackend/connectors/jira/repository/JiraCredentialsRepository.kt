package com.sprintstart.sprintstartbackend.connectors.jira.repository

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredentialsId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
internal interface JiraCredentialsRepository : JpaRepository<JiraCredential, JiraCredentialsId> {
    @Query(
        """
        SELECT c FROM JiraCredential c
        WHERE c.id.userEmail = :userEmail
        """,
    )
    fun findAllByUserEmail(userEmail: String): List<JiraCredential>
}
