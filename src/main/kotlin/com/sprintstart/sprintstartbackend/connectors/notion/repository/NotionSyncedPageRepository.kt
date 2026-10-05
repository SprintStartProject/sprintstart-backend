package com.sprintstart.sprintstartbackend.connectors.notion.repository

import com.sprintstart.sprintstartbackend.connectors.notion.model.entity.NotionSyncedPage
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface NotionSyncedPageRepository : JpaRepository<NotionSyncedPage, UUID> {
    fun findAllByConnectionId(connectionId: UUID): List<NotionSyncedPage>

    fun findByConnectionIdAndPageId(connectionId: UUID, pageId: String): NotionSyncedPage?

    fun deleteAllByConnectionId(connectionId: UUID)
}
