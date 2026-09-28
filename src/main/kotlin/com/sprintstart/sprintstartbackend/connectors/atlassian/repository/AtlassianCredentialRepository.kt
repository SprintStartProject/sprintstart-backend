package com.sprintstart.sprintstartbackend.connectors.atlassian.repository

import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredential
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredentialId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
internal interface AtlassianCredentialRepository : JpaRepository<AtlassianCredential, AtlassianCredentialId> {
    @Query(
        """
        SELECT c FROM AtlassianCredential c
        WHERE c.id.authId = :authId
        """,
    )
    fun findAllByAuthId(authId: String): List<AtlassianCredential>
}
