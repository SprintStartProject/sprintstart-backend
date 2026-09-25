package com.sprintstart.sprintstartbackend.connectors.notion.repository

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionPageConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
internal interface NotionPageConnectionRepository : JpaRepository<NotionPageConnection, UUID> {
    fun findByIdAndProjectId(id: UUID, projectId: UUID): NotionPageConnection?

    fun findAllByProjectIdOrderByCreatedAtAsc(projectId: UUID): List<NotionPageConnection>

    fun existsByProjectIdAndPageId(projectId: UUID, pageId: String): Boolean

    fun existsByCredentialAuthIdAndCredentialName(authId: String, credentialName: String): Boolean
    fun findAllByCredentialAuthIdAndCredentialName(authId: String, credentialName: String): List<NotionPageConnection>

    fun findAllByAutoUpdateTrueAndSourceEnabledTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(
        now: Instant,
    ): List<NotionPageConnection>
}
