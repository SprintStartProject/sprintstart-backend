package com.sprintstart.sprintstartbackend.connectors.confluence.repository

import com.sprintstart.sprintstartbackend.connectors.confluence.model.entity.ConfluenceCredential
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface ConfluenceCredentialRepository : JpaRepository<ConfluenceCredential, UUID> {
    fun findByConnectionIdAndConnectionProjectId(connectionId: UUID, projectId: UUID): ConfluenceCredential?
}
