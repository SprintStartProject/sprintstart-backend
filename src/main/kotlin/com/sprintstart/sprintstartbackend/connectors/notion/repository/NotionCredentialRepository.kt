package com.sprintstart.sprintstartbackend.connectors.notion.repository

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredential
import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionCredentialId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
internal interface NotionCredentialRepository : JpaRepository<NotionCredential, NotionCredentialId> {
    fun findAllByIdAuthIdOrderByCreatedAtAsc(authId: String): List<NotionCredential>
}
