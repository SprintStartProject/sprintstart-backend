package com.sprintstart.sprintstartbackend.connectors.confluence.repository

import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceSpaceConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface ConfluenceSpaceConnectionRepository : JpaRepository<ConfluenceSpaceConnection, UUID> {
    fun findByIdAndProjectId(id: UUID, projectId: UUID): ConfluenceSpaceConnection?

    fun findAllByProjectIdOrderByCreatedAtAsc(projectId: UUID): List<ConfluenceSpaceConnection>

    fun findAllByOrderByCreatedAtAsc(): List<ConfluenceSpaceConnection>

    fun existsByProjectIdAndBaseUrlAndSpaceId(projectId: UUID, baseUrl: String, spaceId: String): Boolean
}
