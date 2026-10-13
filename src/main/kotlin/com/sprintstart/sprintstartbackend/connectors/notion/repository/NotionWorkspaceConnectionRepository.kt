package com.sprintstart.sprintstartbackend.connectors.notion.repository

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionWorkspaceConnection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
internal interface NotionWorkspaceConnectionRepository : JpaRepository<NotionWorkspaceConnection, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
        "select c from NotionWorkspaceConnection c where c.id = :id and c.projectId = :projectId",
    )
    fun findForUpdate(id: UUID, projectId: UUID): NotionWorkspaceConnection?

    fun findByIdAndProjectId(id: UUID, projectId: UUID): NotionWorkspaceConnection?

    fun findAllByProjectIdOrderByCreatedAtAsc(projectId: UUID): List<NotionWorkspaceConnection>

    fun deleteAllByProjectId(projectId: UUID)

    fun findAllByIdInAndProjectId(ids: Collection<UUID>, projectId: UUID): List<NotionWorkspaceConnection>

    fun existsByProjectIdAndWorkspaceIdAndTokenOwnerId(
        projectId: UUID,
        workspaceId: String,
        tokenOwnerId: String,
    ): Boolean

    fun existsByProjectIdAndWorkspaceIdAndTokenOwnerIdAndIdNot(
        projectId: UUID,
        workspaceId: String,
        tokenOwnerId: String,
        id: UUID,
    ): Boolean

    fun existsByProjectIdAndCredentialAuthIdAndCredentialName(
        projectId: UUID,
        credentialAuthId: String,
        credentialName: String,
    ): Boolean

    fun existsByCredentialAuthIdAndCredentialName(authId: String, credentialName: String): Boolean

    fun findAllByCredentialAuthIdAndCredentialName(
        authId: String,
        credentialName: String,
    ): List<NotionWorkspaceConnection>

    fun findAllByAutoUpdateTrueAndSourceEnabledTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(
        now: Instant,
    ): List<NotionWorkspaceConnection>
}
